package models;

import java.time.LocalDateTime;

/** Registo de auditoria (imutável, append-only). */
public record AuditLog(long id, long userId, String username, String role, String action,
                       String entity, String entityId, String result, String details,
                       LocalDateTime createdAt) {
}
