package repositories;

import models.AuditLog;
import persistence.BankStore;

import java.util.List;
import java.util.stream.Collectors;

/** Registo de auditoria (append-only). */
public class AuditRepository {

    private static BankStore store() { return BankStore.get(); }

    public synchronized void record(long userId, String username, String role, String action,
                                    String entity, String entityId, String result, String details) {
        long id = store().auditLogs.stream().mapToLong(AuditLog::id).max().orElse(0) + 1;
        store().auditLogs.add(new AuditLog(id, userId, username, role, action,
                entity, entityId, result, details, java.time.LocalDateTime.now()));
        store().save();
    }

    /** Últimos registos, opção por acção (ex.: "DEPOSITO"); do mais recente. */
    public List<AuditLog> recent(int limit, String actionFilter) {
        List<AuditLog> list = store().auditLogs.stream()
                .sorted(java.util.Comparator.comparingLong(AuditLog::id).reversed())
                .collect(Collectors.toList());
        if (actionFilter != null && !actionFilter.isBlank() && !actionFilter.equals("TODAS")) {
            list = list.stream().filter(a -> a.action().equalsIgnoreCase(actionFilter)).collect(Collectors.toList());
        }
        return list.stream().limit(Math.max(1, limit)).collect(Collectors.toList());
    }
}
