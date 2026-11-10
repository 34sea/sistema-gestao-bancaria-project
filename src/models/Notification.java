package models;

import java.time.LocalDateTime;

/** Notificação dirigida à credencial do cliente. */
public record Notification(long id, long userId, String title, String message, String category,
                           boolean read, LocalDateTime createdAt) {
}
