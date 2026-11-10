package repositories;

import models.Client;
import persistence.BankStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Repositório de clientes sobre o armazenamento local. */
public class ClientRepository {

    private static BankStore store() { return BankStore.get(); }

    public synchronized long insert(Client c) {
        c.id = nextId();
        if (c.status == null || c.status.isBlank()) c.status = "ACTIVO";
        if (c.createdAt == null) c.createdAt = java.time.LocalDateTime.now();
        c.updatedAt = c.createdAt;
        store().clients.add(c);
        store().save();
        return c.id;
    }

    public synchronized void update(Client c) {
        c.updatedAt = java.time.LocalDateTime.now();
        store().save();
    }

    public synchronized void setStatus(long id, String status) {
        Client c = findById(id);
        if (c != null) c.status = status;
        store().save();
    }

    public Client findById(long id) {
        return store().clients.stream().filter(c -> c.id == id).findFirst().orElse(null);
    }

    public Client findByDocument(String documentNumber) {
        String d = documentNumber == null ? "" : documentNumber.trim();
        return store().clients.stream()
                .filter(c -> c.documentNumber != null && c.documentNumber.equalsIgnoreCase(d))
                .findFirst().orElse(null);
    }

    public Client findByEmail(String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        return store().clients.stream()
                .filter(c -> c.email != null && c.email.equalsIgnoreCase(e))
                .findFirst().orElse(null);
    }

    public List<Client> search(String query) {
        List<Client> all = new ArrayList<>(store().clients);
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            all = new ArrayList<>(all.stream().filter(c ->
                    (c.fullName != null && c.fullName.toLowerCase().contains(q))
                            || (c.documentNumber != null && c.documentNumber.toLowerCase().contains(q))
                            || (c.city != null && c.city.toLowerCase().contains(q))
                            || (c.email != null && c.email.toLowerCase().contains(q))
                            || (c.portalUsername != null && c.portalUsername.toLowerCase().contains(q))).toList());
        }
        all.sort(Comparator.comparingLong((Client c) -> c.id).reversed());
        return all;
    }

    public List<Client> all() { return store().clients; }
    private long nextId() {
        return store().clients.stream().mapToLong(c -> c.id).max().orElse(0) + 1;
    }
}
