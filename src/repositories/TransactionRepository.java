package repositories;

import models.Transaction;
import persistence.BankStore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Histórico de movimentos (append-only, guardado em data/transactions.csv). */
public class TransactionRepository {

    private static BankStore store() { return BankStore.get(); }

    public synchronized void insert(Transaction t) {
        store().transactions.add(t);
        store().save();
    }

    public Optional<Transaction> byCode(String code) {
        String c = code == null ? "" : code.trim();
        return store().transactions.stream().filter(t -> t.code() != null && t.code().equalsIgnoreCase(c)).findFirst();
    }

    public List<Transaction> all() {
        return store().transactions.stream()
                .sorted(Comparator.comparingLong(Transaction::id).reversed())
                .toList();
    }

    /** Movimentos de uma conta, do mais recente para o mais antigo. */
    public List<Transaction> byAccount(long accountId) {
        return store().transactions.stream()
                .filter(t -> t.accountId() == accountId)
                .sorted(Comparator.comparingLong(Transaction::id).reversed())
                .toList();
    }

    public List<Transaction> allRaw() { return store().transactions; }
}
