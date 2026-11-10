package services;

import exceptions.BusinessException;
import exceptions.ForbiddenException;
import models.Account;
import models.AccountType;
import models.User;
import persistence.BankStore;
import repositories.AccountRepository;
import repositories.AuditRepository;
import repositories.NotificationRepository;
import repositories.TransactionRepository;
import utils.Ids;
import utils.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Gestão do ciclo de vida das contas e aplicação de juros (simulação). */
public class AccountService {

    private final AccountRepository accounts = new AccountRepository();
    private final TransactionRepository transactions = new TransactionRepository();
    private final NotificationRepository notifications = new NotificationRepository();
    private final AuditRepository audits = new AuditRepository();

    /** Abertura de conta: apenas funcionários; cliente tem de estar activo. */
    public String open(User actor, long clientId, String typeCode) throws Exception {
        requireStaff(actor);
        if (typeCode == null || typeCode.isBlank()) throw new BusinessException("Escolha o tipo de conta.");
        AccountType type = AccountType.byCode(typeCode);
        if (type == null) throw new BusinessException("Tipo de conta inválido.");
        var client = new repositories.ClientRepository().findById(clientId);
        if (client == null) throw new BusinessException("Cliente não encontrado.");
        if (!"ACTIVO".equals(client.status)) {
            throw new BusinessException("Só é possível abrir contas a clientes activos.");
        }
        return BankStore.writeOperation(v -> {
            Account acc = accounts.open(clientId, client.fullName, type.code(), type.name());
            audits.record(actor.id(), actor.username(), actor.role(), "ABERTURA_CONTA", "CONTA",
                    acc.accountNumber, "CONCLUIDA", "Tipo: " + typeCode);
            return acc.accountNumber;
        });
    }

    /** Bloquear / desbloquear / encerrar. */
    public void changeStatus(User actor, String accountNumber, String newStatus) throws Exception {
        requireStaff(actor);
        if (!List.of("ACTIVA", "BLOQUEADA", "ENCERRADA").contains(newStatus)) {
            throw new BusinessException("Estado de conta inválido.");
        }
        if ("ENCERRADA".equals(newStatus) && !actor.isAdmin()) {
            throw new BusinessException("O encerramento de contas é exclusivo do administrador.");
        }
        BankStore.writeOperation(v -> {
            Account acc = accounts.byNumber(accountNumber)
                    .orElseThrow(() -> new BusinessException("Conta não encontrada."));
            if ("ENCERRADA".equals(newStatus) && acc.balance.compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException("A conta tem saldo de " + Money.format(acc.balance)
                        + ". Encerramento exige saldo zero.");
            }
            accounts.setStatus(acc.id, newStatus);
            audits.record(actor.id(), actor.username(), actor.role(), "ESTADO_CONTA", "CONTA",
                    accountNumber, "CONCLUIDA", "Novo estado: " + newStatus);
            if ("BLOQUEADA".equals(newStatus)) {
                notifications.createForClient(acc.clientId, "Conta bloqueada",
                        "A conta " + accountNumber + " foi bloqueada pela instituição.", "BLOQUEIO");
            }
            return null;
        });
    }

    /** Aplica a taxa simulada nas contas poupança activas com saldo > 0 (administrador apenas). */
    public int applyInterest(User actor) throws Exception {
        if (actor == null || !actor.isAdmin()) {
            throw new BusinessException("Apenas o administrador aplica juros.");
        }
        return BankStore.writeOperation(v -> {
            int count = 0;
            for (Account acc : List.copyOf(accounts.allRaw())) {
                AccountType type = AccountType.byCode(acc.accountTypeCode);
                if (type == null || !"POUPANCA".equals(type.code())) continue;
                if (!"ACTIVA".equals(acc.status)) continue;
                BigDecimal balance = Money.scaled(acc.balance);
                if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal interest = balance.multiply(type.interestRate()).setScale(2, RoundingMode.DOWN);
                if (interest.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal after = balance.add(interest);
                accounts.registerMovement(acc, after, interest);
                transactions.insert(new models.Transaction(
                        nextTxId(), Ids.transactionCode(), acc.id, acc.accountNumber, null, null, null, null, null,
                        "JUROS", interest, balance, after, "CONCLUIDA",
                        "Juros simulados " + type.ratePercent(), actor.username(), java.time.LocalDateTime.now()));
                notifications.createForClient(acc.clientId, "Juros aplicados",
                        "Juros de " + Money.format(interest) + " creditados na sua conta poupança.", "JUROS");
                count++;
            }
            audits.record(actor.id(), actor.username(), actor.role(), "APLICAR_JUROS", "CONTAS",
                    null, "CONCLUIDA", count + " contas actualizadas");
            return count;
        });
    }

    public List<Account> list(String search, String status) {
        return accounts.all(search, status);
    }

    public List<Account> byClient(long clientId) {
        return accounts.byClient(clientId);
    }

    public Account byNumber(String number) {
        return accounts.byNumber(number).orElse(null);
    }

    /** Cliente: apenas as contas dele, respeitando a regra fundamental de isolamento. */
    public List<Account> accountsForClient(User actor) throws Exception {
        if (actor == null || !actor.isClient()) throw new BusinessException("Contexto de cliente inválido.");
        return byClient(actor.clientIdOrZero());
    }

    private void requireStaff(User actor) {
        if (actor == null || !actor.hasStaffRights()) {
            throw new ForbiddenException("Operação restrita a funcionários.");
        }
    }

    private long nextTxId() {
        return transactions.allRaw().stream().mapToLong(models.Transaction::id).max().orElse(0) + 1;
    }
}
