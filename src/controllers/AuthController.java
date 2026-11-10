package controllers;

import models.User;
import services.AuthService;

/** Adaptador de autenticação para as vistas Swing. */
public class AuthController {

    private final AuthService service = new AuthService();

    /** Utilizador autenticado ou null. */
    public User login(String login, String password) throws Exception {
        return service.login(login, password);
    }

    public void changePassword(User actor, String current, String next) throws Exception {
        service.changePassword(actor, current, next);
    }
}
