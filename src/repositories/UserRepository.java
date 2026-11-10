package repositories;

import models.Client;
import models.User;
import persistence.BankStore;
import persistence.BankStore.UserRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Utilizador (credenciais de acesso) sobre o armazenamento local. */
public class UserRepository {

    private final ClientRepository clients = new ClientRepository();

    private static BankStore store() { return BankStore.get(); }

    public synchronized long create(String username, String fullName, String email,
                                    String passwordHash, String role, Long clientId,
                                    String position, String department) {
        UserRow u = new UserRow();
        u.id = nextId();
        u.username = username;
        u.fullName = fullName;
        u.email = email;
        u.passwordHash = passwordHash;
        u.role = role;
        u.clientId = clientId;
        u.active = true;
        u.position = position;
        u.department = department;
        store().users.add(u);
        store().save();
        return u.id;
    }

    public boolean existsUsername(String username) {
        String x = username == null ? "" : username.trim().toLowerCase();
        return store().users.stream().anyMatch(u -> u.username != null && u.username.toLowerCase().equals(x));
    }

    /** Encontra a credencial por nome de utilizador, e-mail ou (clientes) número de documento. */
    public Optional<UserRow> findRowByLogin(String login) {
        if (login == null) return Optional.empty();
        String l = login.trim();
        for (UserRow u : store().users) {
            boolean match = (u.username != null && u.username.equalsIgnoreCase(l))
                    || (u.email != null && u.email.equalsIgnoreCase(l));
            if (!match && "CLIENTE".equals(u.role)) {
                Client c = u.clientId == null ? null : clients.findById(u.clientId);
                match = c != null && c.documentNumber != null && c.documentNumber.equalsIgnoreCase(l);
            }
            if (match) return Optional.of(u);
        }
        return Optional.empty();
    }

    public User findById(long id) {
        UserRow u = rawById(id);
        return u == null ? null : toUser(u);
    }

    public Optional<User> byUsername(String username) {
        String x = username == null ? "" : username.trim().toLowerCase();
        return store().users.stream()
                .filter(u -> u.username != null && u.username.toLowerCase().equals(x))
                .findFirst().map(this::toUser);
    }

    public UserRow rawById(long id) {
        return store().users.stream().filter(u -> u.id == id).findFirst().orElse(null);
    }

    public synchronized void setActive(long userId, boolean active) {
        UserRow u = rawById(userId);
        if (u != null) u.active = active;
        store().save();
    }

    public synchronized void updatePassword(long userId, String passwordHash) {
        UserRow u = rawById(userId);
        if (u != null) u.passwordHash = passwordHash;
        store().save();
    }

    /** Lista vectores compatíveis com as tabelas de UI:
     *  [id, username, fullName, email, estado, role, departamento]. */
    public List<String[]> listRows(String role, String search) {
        List<String[]> out = new ArrayList<>();
        for (UserRow u : sorted()) {
            if (role != null && !role.isBlank() && !role.equals(u.role)) continue;
            boolean ok = search == null || search.isBlank()
                    || (u.username != null && u.username.toLowerCase().contains(search.toLowerCase()))
                    || (u.fullName != null && u.fullName.toLowerCase().contains(search.toLowerCase()));
            if (!ok) continue;
            out.add(new String[]{
                    String.valueOf(u.id), u.username, u.fullName, u.email == null ? "" : u.email,
                    u.active ? "ACTIVO" : "INACTIVO", u.role, u.department == null ? "" : u.department});
        }
        return out;
    }

    public List<User> all() {
        List<User> out = new ArrayList<>();
        for (UserRow u : sorted()) out.add(toUser(u));
        return out;
    }

    private List<UserRow> sorted() {
        return store().users.stream().sorted(Comparator.comparingLong(u -> u.id)).toList();
    }

    private User toUser(UserRow u) {
        return new User(u.id, u.username, u.fullName, u.role, u.clientId, u.email);
    }

    private long nextId() {
        return store().users.stream().mapToLong(u -> u.id).max().orElse(0) + 1;
    }
}
