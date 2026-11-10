package services;

import exceptions.BusinessException;
import exceptions.ForbiddenException;
import models.Account;
import models.AccountType;
import models.Transaction;
import models.User;
import repositories.AccountRepository;
import repositories.ClientRepository;
import repositories.TransactionRepository;
import utils.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas agregadas e paginadas para dashboards, históricos e relatórios. */
public class QueryService {

    private final TransactionRepository tx = new TransactionRepository();
    private final AccountRepository accounts = new AccountRepository();
    private final ClientRepository clients = new ClientRepository();

    public record MovementsPage(List<Row> rows, int total, int page, int pageSize) {}

    public record Row(String code, String date, String type, String amount, String direction,
                      String balanceAfter, String status, String description,
                      String accountNumber, String counterparty, String relatedName) {}

    /** Todas as movimentações de um cliente (portal — isolamento por proprietário da conta). */
    public MovementsPage movementsForClient(User actor, String type, String status, String accountNumber,
                                            LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        if (actor == null || !actor.isClient()) throw new BusinessException("Contexto de cliente inválido.");
        List<Account> ownAccounts = accounts.byClient(actor.clientIdOrZero());
        List<Long> ownIds = new ArrayList<>();
        List<String> ownNumbers = new ArrayList<>();
        for (Account a : ownAccounts) { ownIds.add(a.id); ownNumbers.add(a.accountNumber); }

        List<Transaction> filtered = filter(allTx(), type, status, fromDate, toDate);
        if (accountNumber != null && !accountNumber.isBlank() && !accountNumber.equals("TODAS AS CONTAS")) {
            String n = accountNumber.trim();
            filtered = filtered.stream().filter(t -> n.equals(t.accountNumber())).toList();
        } else {
            filtered = filtered.stream().filter(t -> ownIds.contains(t.accountId())).toList();
        }
        return paginate(filtered, page, pageSize);
    }

    /** Movimentações de uma conta concreta — cliente só vê contas próprias. */
    public MovementsPage movementsForAccount(User actor, String accountNumber, String type, String status,
                                             LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        if (actor == null) throw new BusinessException("Sessão inválida.");
        if (actor.isClient()) {
            if (!accounts.ownedBy(actor.clientIdOrZero(), accountNumber)) {
                throw new BusinessException("A conta pedida não pertence ao cliente autenticado.");
            }
        } else if (!actor.hasStaffRights()) {
            throw new BusinessException("Sem permissão.");
        }
        List<Transaction> filtered = filter(allTx(), type, status, fromDate, toDate);
        filtered = filtered.stream().filter(t -> t.accountNumber().equals(accountNumber)).toList();
        return paginate(filtered, page, pageSize);
    }

    /** Movimentações globais (funcionário / administrador). */
    public MovementsPage allMovements(String type, String status, String accountNumber,
                                      LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        List<Transaction> filtered = filter(allTx(), type, status, fromDate, toDate);
        if (accountNumber != null && !accountNumber.isBlank() && !accountNumber.equals("TODAS AS CONTAS")) {
            String n = accountNumber.trim();
            filtered = filtered.stream().filter(t -> n.equals(t.accountNumber())).toList();
        }
        return paginate(filtered, page, pageSize);
    }

    public Transaction byCode(User actor, String code) {
        Transaction t = tx.byCode(code).orElse(null);
        if (t == null) return null;
        if (actor != null && actor.isClient()) {
            Account a = accounts.byId(t.accountId());
            if (a == null || a.clientId != actor.clientIdOrZero()) {
                throw new BusinessException("Operação não pertence ao cliente autenticado.");
            }
        }
        return t;
    }

    // ------------------------------------------------------------ extractos

    public record StatementData(Account account, List<Row> rows,
                                BigDecimal initialBalance, BigDecimal finalBalance,
                                String fromDate, String toDate) {}

    /** Extracto por conta e período (cliente só da própria conta). */
    public StatementData statement(User actor, String accountNumber, LocalDate fromDate, LocalDate toDate) {
        if (actor == null || (!actor.isClient() && !actor.hasStaffRights())) {
            throw new ForbiddenException("Sem permissão para emitir extractos.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("A data inicial não pode ser posterior à data final.");
        }
        Account acc = accounts.byNumber(accountNumber)
                .orElseThrow(() -> new BusinessException("Conta não encontrada."));
        if (actor.isClient() && acc.clientId != actor.clientIdOrZero()) {
            throw new BusinessException("O extracto pedido não pertence ao cliente autenticado.");
        }

        BigDecimal initial = BigDecimal.ZERO;
        List<Transaction> ascending = tx.byAccount(acc.id).stream()
                .sorted(java.util.Comparator.comparingLong(Transaction::id)).toList();
        LocalDateTime before = fromDate == null ? LocalDateTime.now() : fromDate.atStartOfDay();
        for (Transaction t : ascending) {
            if ("CONCLUIDA".equals(t.status()) && t.createdAt() != null && t.createdAt().isBefore(before)) {
                initial = t.after();
            }
        }

        List<Transaction> period = new ArrayList<>();
        for (Transaction t : ascending) {
            if (t.createdAt() == null) continue;
            if (fromDate != null && t.createdAt().isBefore(fromDate.atStartOfDay())) continue;
            if (toDate != null && t.createdAt().isAfter(toDate.atTime(23, 59, 59))) continue;
            period.add(t);
        }
        period.sort(java.util.Comparator.comparingLong(Transaction::id));

        List<Row> rows = new ArrayList<>();
        BigDecimal lastAfter = initial;
        for (Transaction t : period) {
            if (t.after() != null) lastAfter = t.after();
            rows.add(toRow(t));
        }
        return new StatementData(acc, rows, initial, lastAfter,
                fromDate == null ? "" : fromDate.toString(), toDate == null ? "" : toDate.toString());
    }

    // ------------------------------------------------------------ estatísticas

    /** Estatísticas do livro de movimentos das contas do cliente autenticado. */
    public Map<String, String> clientMovementStats(User actor) {
        if (actor == null || !actor.isClient()) throw new BusinessException("Contexto de cliente inválido.");
        List<Account> own = accounts.byClient(actor.clientIdOrZero());
        List<Long> ownIds = new ArrayList<>();
        for (Account a : own) ownIds.add(a.id);

        BigDecimal entries = BigDecimal.ZERO, exits = BigDecimal.ZERO;
        long count = 0, sent = 0, received = 0;
        for (Transaction t : tx.allRaw()) {
            if (!ownIds.contains(t.accountId()) || !"CONCLUIDA".equals(t.status())) continue;
            count++;
            if (isEntry(t.type())) entries = entries.add(t.amount()); else exits = exits.add(t.amount());
            if ("TRANSFERENCIA_ENVIADA".equals(t.type())) sent++;
            if ("TRANSFERENCIA_RECEBIDA".equals(t.type())) received++;
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("totalEntries", Money.number(entries));
        result.put("totalExits", Money.number(exits));
        result.put("movementCount", String.valueOf(count));
        result.put("transfersSent", String.valueOf(sent));
        result.put("transfersReceived", String.valueOf(received));
        return result;
    }

    /** Indicadores operacionais limitados ao funcionário autenticado. */
    public Map<String, String> employeeDashboardStats(User actor) {
        if (actor == null || !actor.isEmployee()) {
            throw new ForbiddenException("Painel operacional exclusivo de funcionários.");
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clients", String.valueOf(clients.all().size()));
        m.put("accounts", String.valueOf(accounts.allRaw().size()));
        BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO;
        long transactions = 0, transfers = 0;
        for (Transaction t : tx.allRaw()) {
            if (t.performedByName() == null || !t.performedByName().equals(actor.username())) continue;
            if (!"CONCLUIDA".equals(t.status())) continue;
            transactions++;
            if ("DEPOSITO".equals(t.type())) deposits = deposits.add(t.amount());
            if ("LEVANTAMENTO".equals(t.type())) withdrawals = withdrawals.add(t.amount());
        }
        m.put("deposits", Money.number(deposits));
        m.put("withdrawals", Money.number(withdrawals));
        m.put("transactions", String.valueOf(transactions));
        m.put("transfers", String.valueOf(countOwnTransfers(actor)));
        return m;
    }

    private long countOwnTransfers(User actor) {
        long n = 0;
        for (var tr : persistence.BankStore.get().transfers) {
            if (actor.username().equals(tr.performedBy) && "CONCLUIDA".equals(tr.status)) n++;
        }
        return n;
    }

    public Map<String, String> dashboardStats() {
        Map<String, String> m = new LinkedHashMap<>();
        long active = 0, blocked = 0, closed = 0;
        BigDecimal totalBalance = BigDecimal.ZERO;
        for (Account a : accounts.allRaw()) {
            if ("ACTIVA".equals(a.status)) active++;
            else if ("BLOQUEADA".equals(a.status)) blocked++;
            else if ("ENCERRADA".equals(a.status)) closed++;
            if (!"ENCERRADA".equals(a.status)) totalBalance = totalBalance.add(a.balance);
        }
        long employeeCount = 0;
        for (var u : persistence.BankStore.get().users) {
            if ("FUNCIONARIO".equals(u.role) && u.active) employeeCount++;
        }
        BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO, transferVolume = BigDecimal.ZERO;
        long transferCount = 0;
        for (Transaction t : tx.allRaw()) {
            if (!"CONCLUIDA".equals(t.status())) continue;
            if ("DEPOSITO".equals(t.type())) deposits = deposits.add(t.amount());
            if ("LEVANTAMENTO".equals(t.type())) withdrawals = withdrawals.add(t.amount());
        }
        for (var tr : persistence.BankStore.get().transfers) {
            if ("CONCLUIDA".equals(tr.status)) { transferCount++; transferVolume = transferVolume.add(tr.amount); }
        }
        m.put("clients", String.valueOf(clients.all().size()));
        m.put("employees", String.valueOf(employeeCount));
        m.put("accounts", String.valueOf(accounts.allRaw().size()));
        m.put("activeAccounts", String.valueOf(active));
        m.put("blockedAccounts", String.valueOf(blocked));
        m.put("closedAccounts", String.valueOf(closed));
        m.put("deposits", Money.number(deposits));
        m.put("withdrawals", Money.number(withdrawals));
        m.put("transferCount", String.valueOf(transferCount));
        m.put("transferVolume", Money.number(transferVolume));
        m.put("totalBalance", Money.number(totalBalance));
        m.put("transactions", String.valueOf(tx.allRaw().size()));
        return m;
    }

    /** Agregados por dia (últimos 14 dias) para depósitos, levantamentos e transferências enviadas. */
    public Map<String, List<String[]>> dailySeriesByType() {
        Map<String, List<String[]>> out = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(13);
        for (String type : List.of("DEPOSITO", "LEVANTAMENTO", "TRANSFERENCIA_ENVIADA")) {
            List<String[]> rows = new ArrayList<>();
            for (LocalDate d : lastDays()) {
                BigDecimal sum = BigDecimal.ZERO; long n = 0;
                for (Transaction t : tx.allRaw()) {
                    if (t.type().equals(type) && "CONCLUIDA".equals(t.status())
                            && t.createdAt() != null && !t.createdAt().toLocalDate().isBefore(start)) {
                        if (t.createdAt().toLocalDate().equals(d)) { sum = sum.add(t.amount()); n++; }
                    }
                }
                rows.add(new String[]{d.toString(), Money.number(sum), String.valueOf(n)});
            }
            out.put(type, rows);
        }
        return out;
    }

    /** Volume diário total (contagem e soma) para o gráfico de evolução. */
    public List<String[]> dailyVolume() {
        List<String[]> out = new ArrayList<>();
        for (LocalDate d : lastDays()) {
            long n = 0; BigDecimal sum = BigDecimal.ZERO;
            for (Transaction t : tx.allRaw()) {
                if ("CONCLUIDA".equals(t.status()) && t.createdAt() != null
                        && t.createdAt().toLocalDate().equals(d)) { n++; sum = sum.add(t.amount()); }
            }
            out.add(new String[]{d.toString(), String.valueOf(n), Money.number(sum)});
        }
        return out;
    }

    /** Distribuição de contas por tipo. */
    public List<String[]> accountTypeDistribution() {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (Account a : accounts.allRaw()) {
            if ("ENCERRADA".equals(a.status)) continue;
            counts.computeIfAbsent(a.accountTypeName, k -> new long[]{0})[0]++;
            sums.merge(a.accountTypeName, a.balance, BigDecimal::add);
        }
        List<String[]> out = new ArrayList<>();
        for (var e : counts.entrySet()) {
            out.add(new String[]{e.getKey(), String.valueOf(e.getValue()[0]), Money.number(sums.get(e.getKey()))});
        }
        out.sort((x, y) -> Long.compare(Long.parseLong(y[1]), Long.parseLong(x[1])));
        return out;
    }

    // ------------------------------------------------------------ utilitários

    private List<Transaction> filter(List<Transaction> base, String type, String status,
                                     LocalDate fromDate, LocalDate toDate) {
        List<Transaction> list = base;
        if (type != null && !type.isBlank() && !type.equals("TODOS")) {
            String ty = type;
            list = list.stream().filter(t -> ty.equals(t.type())).toList();
        }
        if (status != null && !status.isBlank() && !status.equals("TODOS")) {
            String st = status;
            list = list.stream().filter(t -> st.equals(t.status())).toList();
        }
        if (fromDate != null) {
            LocalDateTime from = fromDate.atStartOfDay();
            list = list.stream().filter(t -> t.createdAt() != null && !t.createdAt().isBefore(from)).toList();
        }
        if (toDate != null) {
            LocalDateTime to = toDate.atTime(23, 59, 59);
            list = list.stream().filter(t -> t.createdAt() != null && !t.createdAt().isAfter(to)).toList();
        }
        return list;
    }

    private MovementsPage paginate(List<Transaction> list, int page, int pageSize) {
        int pSize = Math.max(5, Math.min(100, pageSize));
        int pNum = Math.max(1, page);
        int from = (pNum - 1) * pSize;
        List<Row> rows = new ArrayList<>();
        for (int i = from; i < Math.min(list.size(), from + pSize); i++) rows.add(toRow(list.get(i)));
        return new MovementsPage(rows, list.size(), pNum, pSize);
    }

    private Row toRow(Transaction t) {
        return new Row(t.code(),
                t.createdAt() == null ? "" : t.createdAt().toString().replace('T', ' '),
                t.type(), Money.number(t.amount()), directionOf(t.type()),
                Money.number(t.after()), t.status(), t.description(),
                t.accountNumber(), t.counterpartyAccountNumber() == null ? "" : t.counterpartyAccountNumber(),
                t.counterpartyName() == null ? "" : t.counterpartyName());
    }

    private List<Transaction> allTx() {
        return tx.all().stream()
                .sorted((a, b) -> {
                    if (a.createdAt() == null && b.createdAt() == null) return Long.compare(b.id(), a.id());
                    if (a.createdAt() == null) return 1;
                    if (b.createdAt() == null) return -1;
                    int c = b.createdAt().compareTo(a.createdAt());
                    return c != 0 ? c : Long.compare(b.id(), a.id());
                })
                .toList();
    }

    private List<LocalDate> lastDays() {
        List<LocalDate> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) days.add(today.minusDays(i));
        return days;
    }

    private String directionOf(String type) {
        return switch (type) {
            case "DEPOSITO", "TRANSFERENCIA_RECEBIDA", "JUROS" -> "ENTRADA";
            case "LEVANTAMENTO", "TRANSFERENCIA_ENVIADA" -> "SAIDA";
            default -> "—";
        };
    }

    private boolean isEntry(String type) {
        return "DEPOSITO".equals(type) || "TRANSFERENCIA_RECEBIDA".equals(type) || "JUROS".equals(type);
    }
}
