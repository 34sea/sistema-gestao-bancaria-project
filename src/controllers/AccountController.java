package controllers;

import models.Account;
import models.User;
import services.AccountService;

import java.util.List;

/** Adaptador da gestão de contas. */
public class AccountController {

    private final AccountService service = new AccountService();

    public String open(User actor, long clientId, String typeCode) throws Exception {
        return service.open(actor, clientId, typeCode);
    }

    public void changeStatus(User actor, String accountNumber, String newStatus) throws Exception {
        service.changeStatus(actor, accountNumber, newStatus);
    }

    public int applyInterest(User actor) throws Exception {
        return service.applyInterest(actor);
    }

    public List<Account> list(String search, String status) throws Exception {
        return service.list(search, status);
    }

    public List<Account> byClient(long clientId) throws Exception {
        return service.byClient(clientId);
    }

    public Account byNumber(String number) throws Exception {
        return service.byNumber(number);
    }

    public List<Account> accountsForClient(User actor) throws Exception {
        return service.accountsForClient(actor);
    }
}
