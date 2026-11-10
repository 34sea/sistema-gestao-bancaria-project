package services;

import exceptions.BusinessException;
import exceptions.ForbiddenException;
import models.Account;
import models.Transaction;
import models.User;
import persistence.BankStore;
import repositories.AccountRepository;
import repositories.AuditRepository;
import repositories.NotificationRepository;
import repositories.TransactionRepository;
import repositories.TransferRepository;
import persistence.BankStore.TransferRow;
import utils.Ids;
import utils.Money;

import java.math.BigDecimal;

/**
 * Regras de negócio das operações financeiras: depósitos, levantamentos e
 * transferências. Cada operação corre numa única operação de escrita
 * sincronizada (equivalente à transacção SQL da versão com base de dados).
 */
public class BankingService {

    private final AccountRepository accounts = new AccountRepository();
    private final TransactionRepository transactions = new TransactionRepository();
    private final TransferRepository transfers = new TransferRepository();
    private final NotificationRepository notifications = new NotificationRepository();
    private final AuditRepository auditRepository = new AuditRepository();

    // ------------------------------------------------------------------ API

    public OperationResult deposit(User actor, String accountNumber, BigDecimal amount, String note) throws Exception {
        if (!Money.isValidAmount(amount)) {
            throw new BusinessException("O valor do depósito deve ser positivo e ter no máximo duas casas decimais.");
        }
        requireStaff(actor);
        return BankStore.writeOperation(v -> doDeposit(actor, accountNumber, amount, note));
    }

    public OperationResult withdraw(User actor, String accountNumber, BigDecimal amount, String note) throws Exception {
        if (!Money.isValidAmount(amount)) {
            throw new BusinessException("O valor do levantamento deve ser positivo e ter no máximo duas casas decimais.");
        }
        requireStaff(actor);
        return BankStore.writeOperation(v -> doWithdraw(actor, accountNumber, amount, note));
    }

    public TransferResult transfer(User actor, String sourceNumber, String targetNumber, BigDecimal amount,
                                   String description, String idempotencyKey) throws Exception {
        if (actor == null) throw new ForbiddenException("Sessão inválida.");
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (!Money.isValidAmount(value)) {
            throw new BusinessException("O valor da transferência deve ser positivo e ter no máximo duas casas decimais.");
        }
        if (targetNumber == null || targetNumber.isBlank()) {
            throw new BusinessException("Indique a conta de destino.");
        }
        String dst = targetNumber.trim();
        if (sourceNumber != null && dst.equals(sourceNumber.trim())) {
            throw new BusinessException("A conta de destino tem de ser diferente da conta de origem.");
        }
        if (!actor.hasStaffRights() && !accounts.ownedBy(actor.clientIdOrZero(), sourceNumber)) {
            throw new ForbiddenException("Não pode movimentar contas que não lhe pertencem.");
        }
        return BankStore.writeOperation(v -> doTransfer(actor, sourceNumber, dst, value, description, idempotencyKey));
    }

    /** Valida uma conta de destino para apresentar o beneficiário no formulário. */
    public Beneficiary previewBeneficiary(User actor, String accountNumber) {
        if (actor == null || (!actor.isClient() && !actor.hasStaffRights())) {
            throw new ForbiddenException("Sessão sem permissão para consultar beneficiários.");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BusinessException("Indique a conta de destino.");
        }
        Account acc = accounts.byNumber(accountNumber)
                .orElseThrow(() -> new BusinessException("Conta de destino não encontrada."));
        return new Beneficiary(acc.accountNumber, acc.clientName, acc.currency, acc.status);
    }

    public record Beneficiary(String accountNumber, String holderName, String currency, String status) {}

    public record OperationResult(String code, String accountNumber, BigDecimal amount,
                                  BigDecimal before, BigDecimal after) {}

    public record TransferResult(String code, String sourceNumber, String targetNumber,
                                 String sourceHolder, String targetHolder, BigDecimal amount,
                                 BigDecimal sourceBefore, BigDecimal sourceAfter, String description) {}

    // ------------------------------------------------------------- detalhe

    private OperationResult doDeposit(User actor, String number, BigDecimal amount, String note) {
        Account acc = activeOrThrow(number);
        clientMustBeActive(acc.clientId);
        BigDecimal before = acc.balance;
        BigDecimal after = Money.scaled(before.add(amount));
        String code = Ids.transactionCode();
        accounts.registerMovement(acc, after, null);
        transactions.insert(new Transaction(
                nextTxId(), code, acc.id, acc.accountNumber, null, null, null, null, null,
                "DEPOSITO", Money.scaled(amount), before, after, "CONCLUIDA",
                note == null || note.isBlank() ? "Depósito em balcão" : note,
                actor.username(), java.time.LocalDateTime.now()));
        notifications.createForClient(acc.clientId, "Depósito realizado",
                "Foi efectuado um depósito de " + Money.format(amount) + " na conta " + number, "DEPOSITO");
        auditRepository.record(actor.id(), actor.username(), actor.role(), "DEPOSITO", "CONTA",
                acc.accountNumber, "CONCLUIDA", number + " | " + Money.format(amount));
        return new OperationResult(code, number, Money.scaled(amount), before, after);
    }

    private OperationResult doWithdraw(User actor, String number, BigDecimal amount, String note) {
        Account acc = activeOrThrow(number);
        clientMustBeActive(acc.clientId);
        var type = models.AccountType.byCode(acc.accountTypeCode);
        if (type != null && !type.allowsWithdrawal()) {
            throw new BusinessException("Este tipo de conta não permite levantamentos.");
        }
        if (acc.balance.compareTo(amount) < 0) {
            throw new BusinessException("Saldo insuficiente. Saldo disponível: " + Money.format(acc.balance) + ".");
        }
        BigDecimal before = acc.balance;
        BigDecimal after = Money.scaled(before.subtract(amount));
        String code = Ids.transactionCode();
        accounts.registerMovement(acc, after, null);
        transactions.insert(new Transaction(
                nextTxId(), code, acc.id, acc.accountNumber, null, null, null, null, null,
                "LEVANTAMENTO", Money.scaled(amount), before, after, "CONCLUIDA",
                note == null || note.isBlank() ? "Levantamento em balcão" : note,
                actor.username(), java.time.LocalDateTime.now()));
        auditRepository.record(actor.id(), actor.username(), actor.role(), "LEVANTAMENTO", "CONTA",
                acc.accountNumber, "CONCLUIDA", number + " | " + Money.format(amount));
        return new OperationResult(code, number, Money.scaled(amount), before, after);
    }

    private TransferResult doTransfer(User actor, String srcNo, String dstNo, BigDecimal amount,
                                      String description, String idempotencyKey) {
        // Reenvio idêntico devolve a transferência já concluída (protecção anti-duplicação).
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = transfers.byIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TransferRow row = existing.get();
                Account src0 = accounts.byId(row.sourceAccountId);
                Account dst0 = accounts.byId(row.targetAccountId);
                boolean sameRequest = src0 != null && dst0 != null
                        && src0.accountNumber.equals(srcNo) && dst0.accountNumber.equals(dstNo)
                        && row.amount.compareTo(amount) == 0;
                if (!sameRequest) {
                    throw new BusinessException("A chave de idempotência já foi utilizada noutra transferência.");
                }
                return existingTransferResult(row);
            }
        }
        Account src = activeOrThrow(srcNo);
        Account dst = activeOrThrow(dstNo);

        clientMustBeActive(src.clientId);
        clientMustBeActive(dst.clientId);
        if (!src.currency.equals(dst.currency)) {
            throw new BusinessException("Moedas incompatíveis entre as contas da transferência.");
        }
        if (src.balance.compareTo(amount) < 0) {
            throw new BusinessException("Saldo insuficiente na conta de origem. Disponível: " + Money.format(src.balance) + ".");
        }

        BigDecimal srcBefore = src.balance, dstBefore = dst.balance;
        BigDecimal srcAfter = Money.scaled(srcBefore.subtract(amount)), dstAfter = Money.scaled(dstBefore.add(amount));

        String tcode = Ids.transferCode();
        String desc = description == null || description.isBlank() ? "Transferência entre contas" : description;
        String txCode = Ids.transactionCode();

        TransferRow row = new TransferRow();
        row.code = tcode;
        row.sourceAccountId = src.id;
        row.targetAccountId = dst.id;
        row.amount = Money.scaled(amount);
        row.description = desc;
        row.performedBy = actor.username();
        row.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
        long transferId = transfers.insert(row);

        accounts.registerMovement(src, srcAfter, null);
        accounts.registerMovement(dst, dstAfter, null);

        transactions.insert(new Transaction(nextTxId(), txCode, src.id, src.accountNumber,
                dst.id, dst.accountNumber, dst.clientName, transferId, tcode, "TRANSFERENCIA_ENVIADA",
                Money.scaled(amount), srcBefore, srcAfter, "CONCLUIDA", desc + " → " + dstNo,
                actor.username(), java.time.LocalDateTime.now()));
        transactions.insert(new Transaction(nextTxId(), Ids.transactionCode(), dst.id, dst.accountNumber,
                src.id, src.accountNumber, src.clientName, transferId, tcode, "TRANSFERENCIA_RECEBIDA",
                Money.scaled(amount), dstBefore, dstAfter, "CONCLUIDA", desc + " ← " + srcNo,
                actor.username(), java.time.LocalDateTime.now()));

        notifications.createForClient(dst.clientId, "Transferência recebida",
                "Recebeu " + Money.format(amount) + " da conta " + srcNo
                        + (description == null || description.isBlank() ? "" : " — " + description),
                "TRANSFERENCIA");
        auditRepository.record(actor.id(), actor.username(), actor.role(), "TRANSFERENCIA", "CONTA",
                src.accountNumber, "CONCLUIDA", src.accountNumber + " → " + dst.accountNumber + " | " + Money.format(amount));

        var txLines = transactions.allRaw();
        BigDecimal sentBefore = null, sentAfter = null;
        for (var t : txLines) {
            if (t.transferId() != null && t.transferId() == transferId && t.accountId() == src.id) {
                sentBefore = t.before(); sentAfter = t.after();
            }
        }
        return new TransferResult(tcode, src.accountNumber, dst.accountNumber,
                src.clientName, dst.clientName, Money.scaled(amount),
                sentBefore == null ? srcBefore : sentBefore,
                sentAfter == null ? srcAfter : sentAfter, desc);
    }

    private TransferResult existingTransferResult(TransferRow row) {
        Account src = accounts.byId(row.sourceAccountId);
        Account dst = accounts.byId(row.targetAccountId);
        BigDecimal before = BigDecimal.ZERO, after = BigDecimal.ZERO;
        for (var t : transactions.allRaw()) {
            if (t.transferId() != null && t.transferId() == row.id && t.accountId() == row.sourceAccountId) {
                before = t.before(); after = t.after();
            }
        }
        return new TransferResult(row.code, src.accountNumber, dst.accountNumber,
                src.clientName, dst.clientName, row.amount, before, after,
                row.description == null ? "Transferência entre contas" : row.description);
    }

    private Account activeOrThrow(String number) {
        Account acc = accounts.byNumber(number)
                .orElseThrow(() -> new BusinessException("Conta não encontrada: " + number));
        if (!"ACTIVA".equals(acc.status)) {
            throw new BusinessException("A conta " + number + " não está activa (estado: " + acc.status + ").");
        }
        return acc;
    }

    private void clientMustBeActive(long clientId) {
        var c = new repositories.ClientRepository().findById(clientId);
        if (c == null) throw new BusinessException("Proprietário da conta inexistente.");
        if (!"ACTIVO".equals(c.status)) {
            throw new BusinessException("O proprietário da conta não está activo (estado: " + c.status + ").");
        }
    }

    private void requireStaff(User actor) {
        if (actor == null || !actor.hasStaffRights()) {
            throw new ForbiddenException("Somente funcionários e administradores realizam esta operação.");
        }
    }

    private long nextTxId() {
        return transactions.allRaw().stream().mapToLong(Transaction::id).max().orElse(0) + 1;
    }
}
