package persistence;

import models.Account;
import models.AuditLog;
import models.Client;
import models.Notification;
import models.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Armazenamento local em ficheiros CSV, na pasta "data" do projecto
 * (configurável via propriedade de sistema -Dbanco.data.dir=...).
 *
 * Todo o estado da aplicação vive em memória durante a execução e é gravado
 * em disco de forma sincronizada após cada operação de escrita, garantindo
 * que os dados sobrevivem ao fecho da aplicação, sem necessidade de Docker,
 * servidores de base de dados ou bibliotecas externas.
 */
public final class BankStore {

    /** Monitor global: as operações de escrita correm dentro de synchronized (LOCK). */
    public static final Object LOCK = new Object();

    private static BankStore instance;

    public static synchronized BankStore get() {
        if (instance == null) instance = new BankStore();
        return instance;
    }

    // -------------------------------------------------------------- dados
    public final List<Client> clients = new ArrayList<>();
    public final List<UserRow> users = new ArrayList<>();
    public final List<Account> accounts = new ArrayList<>();
    public final List<Transaction> transactions = new ArrayList<>();
    public final List<TransferRow> transfers = new ArrayList<>();
    public final List<Notification> notifications = new ArrayList<>();
    public final List<AuditLog> auditLogs = new ArrayList<>();

    /** Utilizador do sistema (credencial de acesso). */
    public static final class UserRow {
        public long id;
        public String username, fullName, email, passwordHash, role;
        public Long clientId;               // apenas para perfis CLIENTE
        public boolean active = true;
        public String position, department; // apenas FUNCIONARIO
    }

    /** Transferência concluída (ligação entre os dois movimentos). */
    public static final class TransferRow {
        public long id;
        public String code;
        public long sourceAccountId, targetAccountId;
        public BigDecimal amount;
        public String description, performedBy, idempotencyKey, status;
        public java.time.LocalDateTime createdAt;
    }

    // -------------------------------------------------------------- ficheiros
    private Path dir;
    private static final String FILE_CLIENTS = "clients.csv";
    private static final String FILE_USERS = "users.csv";
    private static final String FILE_ACCOUNTS = "accounts.csv";
    private static final String FILE_TX = "transactions.csv";
    private static final String FILE_TRANSFERS = "transfers.csv";
    private static final String FILE_NOTIF = "notifications.csv";
    private static final String FILE_AUDIT = "audit.csv";

    private BankStore() {
        String prop = System.getProperty("banco.data.dir", "data");
        dir = Path.of(prop);
    }

    public Path directory() { return dir; }

    /** Carrega os dados de disco (inofensivo na primeira execução, sem ficheiros). */
    public void load() throws IOException {
        Files.createDirectories(dir);
        readCsv(FILE_CLIENTS, p -> {
            Client c = new Client();
            c.id = Long.parseLong(p.get(0)); c.fullName = p.get(1); c.documentNumber = p.get(2);
            c.gender = p.get(3); c.address = p.get(4); c.city = p.get(5); c.phone = p.get(6);
            c.email = blankToNull(p.get(7)); c.status = p.get(8);
            c.birthDate = CsvCodec.date(p.get(9)); c.portalUsername = blankToNull(p.get(10));
            c.createdAt = CsvCodec.ts(p.get(11)); c.updatedAt = CsvCodec.ts(p.get(12));
            clients.add(c);
        }, 13);

        readCsv(FILE_USERS, p -> {
            UserRow u = new UserRow();
            u.id = Long.parseLong(p.get(0)); u.username = p.get(1); u.fullName = p.get(2);
            u.email = blankToNull(p.get(3)); u.passwordHash = p.get(4); u.role = p.get(5);
            u.clientId = p.get(6).isBlank() ? null : Long.parseLong(p.get(6));
            u.active = Boolean.parseBoolean(p.get(7));
            u.position = blankToNull(p.get(8)); u.department = blankToNull(p.get(9));
            users.add(u);
        }, 10);

        readCsv(FILE_ACCOUNTS, p -> {
            Account a = new Account();
            a.id = Long.parseLong(p.get(0)); a.accountNumber = p.get(1);
            a.clientId = Long.parseLong(p.get(2)); a.clientName = p.get(3);
            a.accountTypeCode = p.get(4); a.accountTypeName = p.get(5);
            a.balance = new BigDecimal(p.get(6)); a.currency = p.get(7);
            a.status = p.get(8); a.interestAccrued = new BigDecimal(p.get(9));
            a.openedAt = CsvCodec.ts(p.get(10)); a.lastMovementAt = CsvCodec.ts(p.get(11));
            accounts.add(a);
        }, 12);

        readCsv(FILE_TX, p -> {
            transactions.add(new Transaction(
                    Long.parseLong(p.get(0)), blankToNull(p.get(1)), Long.parseLong(p.get(2)), p.get(3),
                    p.get(4).isBlank() ? null : Long.parseLong(p.get(4)), blankToNull(p.get(5)),
                    blankToNull(p.get(6)), p.get(7).isBlank() ? null : Long.parseLong(p.get(7)),
                    blankToNull(p.get(8)), p.get(9), new BigDecimal(p.get(10)),
                    new BigDecimal(p.get(11)), new BigDecimal(p.get(12)), p.get(13),
                    blankToNull(p.get(14)), blankToNull(p.get(15)), CsvCodec.ts(p.get(16))));
        }, 17);

        readCsv(FILE_TRANSFERS, p -> {
            TransferRow t = new TransferRow();
            t.id = Long.parseLong(p.get(0)); t.code = p.get(1);
            t.sourceAccountId = Long.parseLong(p.get(2)); t.targetAccountId = Long.parseLong(p.get(3));
            t.amount = new BigDecimal(p.get(4)); t.description = blankToNull(p.get(5));
            t.performedBy = p.get(6); t.idempotencyKey = blankToNull(p.get(7));
            t.status = p.get(8); t.createdAt = CsvCodec.ts(p.get(9));
            transfers.add(t);
        }, 10);

        readCsv(FILE_NOTIF, p -> {
            notifications.add(new Notification(
                    Long.parseLong(p.get(0)), Long.parseLong(p.get(1)), p.get(2), p.get(3),
                    p.get(4), Boolean.parseBoolean(p.get(5)), CsvCodec.ts(p.get(6))));
        }, 7);

        readCsv(FILE_AUDIT, p -> {
            auditLogs.add(new AuditLog(
                    Long.parseLong(p.get(0)), Long.parseLong(p.get(1)), p.get(2), p.get(3),
                    p.get(4), p.get(5), p.get(6), p.get(7), blankToNull(p.get(8)), CsvCodec.ts(p.get(9))));
        }, 10);
    }

    private void readCsv(String name, java.util.function.Consumer<List<String>> row, int cols) throws IOException {
        Path f = dir.resolve(name);
        if (!Files.exists(f)) return;
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            List<String> p = CsvCodec.parse(line);
            if (p.size() >= cols) try { row.accept(p); }
            catch (Exception e) { System.err.println("[store] linha inválida em " + name + ": " + line); }
        }
    }

    /** Grava todo o estado em disco (escrita atómica: ficheiro temporário + move). */
    public void save() {
        try {
            Files.createDirectories(dir);
            write(FILE_CLIENTS, clients.stream().sorted(Comparator.comparingLong(c -> c.id)).map(c -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(c.id), c.fullName, c.documentNumber, nvl(c.gender), nvl(c.address),
                    nvl(c.city), nvl(c.phone), nvl(c.email), c.status, CsvCodec.date(c.birthDate),
                    nvl(c.portalUsername), CsvCodec.ts(c.createdAt), CsvCodec.ts(c.updatedAt)))));
            write(FILE_USERS, users.stream().sorted(Comparator.comparingLong(u -> u.id)).map(u -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(u.id), u.username, u.fullName, nvl(u.email), u.passwordHash, u.role,
                    u.clientId == null ? "" : String.valueOf(u.clientId), String.valueOf(u.active),
                    nvl(u.position), nvl(u.department)))));
            write(FILE_ACCOUNTS, accounts.stream().sorted(Comparator.comparingLong(a -> a.id)).map(a -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(a.id), a.accountNumber, String.valueOf(a.clientId), a.clientName,
                    a.accountTypeCode, a.accountTypeName, a.balance.toPlainString(), a.currency, a.status,
                    a.interestAccrued.toPlainString(), CsvCodec.ts(a.openedAt), CsvCodec.ts(a.lastMovementAt)))));
            write(FILE_TX, transactions.stream().map(t -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(t.id()), nvl(t.code()), String.valueOf(t.accountId()), t.accountNumber(),
                    t.counterpartyAccountId() == null ? "" : String.valueOf(t.counterpartyAccountId()),
                    nvl(t.counterpartyAccountNumber()), nvl(t.counterpartyName()),
                    t.transferId() == null ? "" : String.valueOf(t.transferId()), nvl(t.transferCode()),
                    t.type(), t.amount().toPlainString(), t.before().toPlainString(), t.after().toPlainString(),
                    t.status(), nvl(t.description()), nvl(t.performedByName()), CsvCodec.ts(t.createdAt())))));
            write(FILE_TRANSFERS, transfers.stream().map(t -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(t.id), t.code, String.valueOf(t.sourceAccountId), String.valueOf(t.targetAccountId),
                    t.amount.toPlainString(), nvl(t.description), t.performedBy, nvl(t.idempotencyKey),
                    t.status, CsvCodec.ts(t.createdAt)))));
            write(FILE_NOTIF, notifications.stream().map(n -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(n.id()), String.valueOf(n.userId()), n.title(), n.message(),
                    n.category(), String.valueOf(n.read()), CsvCodec.ts(n.createdAt())))));
            write(FILE_AUDIT, auditLogs.stream().map(a -> CsvCodec.line(java.util.Arrays.asList(
                    String.valueOf(a.id()), String.valueOf(a.userId()), a.username() == null ? "" : a.username(),
                    a.role() == null ? "" : a.role(), a.action() == null ? "" : a.action(),
                    a.entity() == null ? "" : a.entity(), a.entityId() == null ? "" : a.entityId(),
                    a.result() == null ? "" : a.result(), nvl(a.details()), CsvCodec.ts(a.createdAt())))));
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível guardar os dados em " + dir.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    private void write(String name, java.util.stream.Stream<String> lines) throws IOException {
        Path tmp = dir.resolve(name + ".tmp");
        try (var w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (String l : lines.toList()) { w.write(l); w.write('\n'); }
        }
        Path target = dir.resolve(name);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.AccessDeniedException e) {
            // fallback: move simples (comum em alguns sistemas de ficheiros do Windows)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String nvl(String v)  { return v == null ? "" : v; }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v; }

    /** Executa uma operação de escrita de forma sincronizada e grava no fim. */
    public static <R> R writeOperation(Function<Void, R> action) {
        synchronized (LOCK) {
            R result = action.apply(null);
            get().save();
            return result;
        }
    }
}
