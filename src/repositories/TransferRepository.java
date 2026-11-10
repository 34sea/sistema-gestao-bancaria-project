package repositories;

import persistence.BankStore;
import persistence.BankStore.TransferRow;

import java.util.List;
import java.util.Optional;

/** Transferências concluídas (protecção de idempotência). */
public class TransferRepository {

    private static BankStore store() { return BankStore.get(); }

    public synchronized long insert(TransferRow t) {
        t.id = nextId();
        t.createdAt = java.time.LocalDateTime.now();
        t.status = "CONCLUIDA";
        store().transfers.add(t);
        store().save();
        return t.id;
    }

    public Optional<TransferRow> byIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return store().transfers.stream().filter(t -> key.equals(t.idempotencyKey)).findFirst();
    }

    public List<TransferRow> allRaw() { return store().transfers; }

    private long nextId() {
        return store().transfers.stream().mapToLong(t -> t.id).max().orElse(0) + 1;
    }
}
