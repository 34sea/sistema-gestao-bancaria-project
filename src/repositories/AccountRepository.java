package repositories;

import models.Account;
import persistence.BankStore;
import utils.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Contas bancárias sobre o armazenamento local. */
public class AccountRepository {

    private static BankStore store() { return BankStore.get(); }

    /** Número determinístico: "1" + 8 dígitos do id (ex.: 100000001). */
    private long nextId() {
        return store().accounts.stream().mapToLong(a -> a.id).max().orElse(0) + 1;
    }

    public synchronized Account open(long clientId, String clientName, String typeCode, String typeName) {
        Account a = new Account();
        a.id = nextId();
        a.accountNumber = "1" + String.format("%08d", a.id);
        a.clientId = clientId;
        a.clientName = clientName;
        a.accountTypeCode = typeCode;
        a.accountTypeName = typeName;
        a.currency = "MZN";
        a.status = "ACTIVA";
        a.openedAt = LocalDateTime.now();
        store().accounts.add(a);
        store().save();
        return a;
    }

    public synchronized void setBalance(long id, BigDecimal balance, BigDecimal extraInterest) {
        Account a = byId(id);
        if (a == null) return;
        a.balance = balance.setScale(2, java.math.RoundingMode.DOWN);
        if (extraInterest != null) a.interestAccrued = a.interestAccrued.add(extraInterest);
        store().save();
    }

    public synchronized void setLastMovement(long id, LocalDateTime when) {
        Account a = byId(id);
        if (a != null) { a.lastMovementAt = when; store().save(); }
    }

    public synchronized void setStatus(long id, String status) {
        Account a = byId(id);
        if (a != null) { a.status = status; store().save(); }
    }

    public Account byId(long id) {
        return store().accounts.stream().filter(a -> a.id == id).findFirst().orElse(null);
    }

    public Optional<Account> byNumber(String number) {
        String n = number == null ? "" : number.trim();
        return store().accounts.stream().filter(a -> a.accountNumber.equals(n)).findFirst();
    }

    public List<Account> byClient(long clientId) {
        return store().accounts.stream()
                .filter(a -> a.clientId == clientId)
                .sorted(Comparator.comparingLong(a -> a.id))
                .toList();
    }

    public List<Account> all(String search, String status) {
        List<Account> list = store().accounts;
        return list.stream()
                .filter(a -> status == null || status.isBlank() || status.equals("TODOS") || a.status.equals(status))
                .filter(a -> {
                    if (search == null || search.isBlank()) return true;
                    String q = search.toLowerCase();
                    return a.accountNumber.contains(q)
                            || (a.clientName != null && a.clientName.toLowerCase().contains(q));
                })
                .sorted(java.util.Comparator.comparingLong((Account a) -> a.id).reversed())
                .toList();
    }

    public boolean ownedBy(long clientId, String accountNumber) {
        return store().accounts.stream()
                .anyMatch(a -> a.accountNumber.equals(accountNumber == null ? "" : accountNumber.trim())
                        && a.clientId == clientId);
    }

    public List<Account> allRaw() { return store().accounts; }

    /** Debita a conta e actualiza o momento do último movimento (operação única sincronizada). */
    public synchronized void registerMovement(Account a, java.math.BigDecimal newBalance, BigDecimal extraInterest) {
        a.balance = Money.scaled(newBalance);
        if (extraInterest != null) a.interestAccrued = a.interestAccrued.add(extraInterest);
        a.lastMovementAt = LocalDateTime.now();
        store().save();
    }
}
