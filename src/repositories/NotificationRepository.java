package repositories;

import models.Notification;
import persistence.BankStore;

import java.util.Comparator;
import java.util.List;

/** Notificações dirigidas à credencial do cliente. */
public class NotificationRepository {

    private final UserRepository users = new UserRepository();
    private static BankStore store() { return BankStore.get(); }

    /** Credencial (user id) associada ao cliente; null se não existir. */
    public Long credentialOf(long clientId) {
        for (var u : store().users) {
            if ("CLIENTE".equals(u.role) && u.clientId != null && u.clientId == clientId) return u.id;
        }
        return null;
    }

    public synchronized void create(long userId, String title, String message, String category) {
        long id = store().notifications.stream().mapToLong(Notification::id).max().orElse(0) + 1;
        store().notifications.add(new Notification(id, userId, title, message,
                category == null ? "GERAL" : category, false, java.time.LocalDateTime.now()));
        store().save();
    }

    public synchronized void createForClient(long clientId, String title, String message, String category) {
        Long uid = credentialOf(clientId);
        if (uid != null) create(uid, title, message, category);
    }

    public List<Notification> forUser(long userId) {
        return store().notifications.stream()
                .filter(n -> n.userId() == userId)
                .sorted(Comparator.comparingLong(Notification::id).reversed())
                .toList();
    }

    public long unreadCount(long userId) {
        return forUser(userId).stream().filter(n -> !n.read()).count();
    }

    public synchronized void markRead(long notificationId) {
        store().notifications.stream()
                .filter(n -> n.id() == notificationId)
                .findFirst()
                .ifPresent(n -> { store().notifications.set(store().notifications.indexOf(n),
                        new Notification(n.id(), n.userId(), n.title(), n.message(), n.category(), true, n.createdAt())); });
        store().save();
    }

    public synchronized void markAllRead(long userId) {
        for (int i = 0; i < store().notifications.size(); i++) {
            Notification n = store().notifications.get(i);
            if (n.userId() == userId && !n.read()) {
                store().notifications.set(i, new Notification(n.id(), n.userId(), n.title(),
                        n.message(), n.category(), true, n.createdAt()));
            }
        }
        store().save();
    }
}
