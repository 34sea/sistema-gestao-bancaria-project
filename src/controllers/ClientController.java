package controllers;

import models.Client;
import models.User;
import services.ClientService;

import java.time.LocalDate;
import java.util.List;

/** Adaptador de gestão de clientes. */
public class ClientController {

    private final ClientService service = new ClientService();

    public long register(User actor, String fullName, String documentNumber, LocalDate birthDate,
                         String gender, String address, String city, String phone, String email,
                         String username, String password) throws Exception {
        return service.register(actor, fullName, documentNumber, birthDate, gender, address, city,
                phone, email, username, password);
    }

    public void update(User actor, long clientId, String fullName, String documentNumber, LocalDate birthDate,
                       String gender, String address, String city, String phone, String email) throws Exception {
        service.update(actor, clientId, fullName, documentNumber, birthDate, gender, address, city, phone, email);
    }

    public void setStatus(User actor, long clientId, String status) throws Exception {
        service.setStatus(actor, clientId, status);
    }

    public List<Client> search(String query) throws Exception {
        return service.search(query);
    }

    public Client byId(long id) throws Exception {
        return service.byId(id);
    }

    public void updateOwn(User actor, String phone, String email, String address, String city) throws Exception {
        service.updateOwn(actor, phone, email, address, city);
    }
}
