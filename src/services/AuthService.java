package services;

import exceptions.BusinessException;
import exceptions.ForbiddenException;
import models.User;
import persistence.BankStore;
import persistence.BankStore.UserRow;
import repositories.AuditRepository;
import repositories.UserRepository;
import utils.Passwords;

/** Autenticação (nome de utilizador, e-mail ou documento do cliente) e alteração de senha. */
public class AuthService {

    private final UserRepository users = new UserRepository();
    private final AuditRepository audit = new AuditRepository();

    /** null ou erro quando as credenciais não são válidas. */
    public User login(String login, String password) throws Exception {
        var found = users.findRowByLogin(login);
        if (found.isEmpty()) return null;
        UserRow u = found.get();
        if (!passwordOk(password, u.passwordHash)) return null;
        if (!u.active) return null;
        if ("CLIENTE".equals(u.role)) {
            var client = new repositories.ClientRepository().findById(u.clientId == null ? 0L : u.clientId);
            if (client == null || !"ACTIVO".equals(client.status)) return null;
        }
        User user = users.findById(u.id);
        audit.record(user.id(), user.username(), user.role(), "LOGIN", "USER",
                String.valueOf(user.id()), "CONCLUIDA", null);
        return user;
    }

    /** Alteração de senha própria (exige a senha actual e uma nova senha forte). */
    public void changePassword(User actor, String current, String next) throws Exception {
        if (actor == null) throw new ForbiddenException("Sessão inválida.");
        utils.Validation.checkPassword(next);
        if (next.equals(current)) {
            throw new exceptions.BusinessException("A nova palavra-passe deve ser diferente da actual.");
        }
        UserRow row = users.rawById(actor.id());
        if (row == null) throw new exceptions.BusinessException("Utilizador não encontrado.");
        if (!passwordOk(current, row.passwordHash)) {
            throw new exceptions.BusinessException("A palavra-passe actual está incorrecta.");
        }
        String hash = Passwords.hash(next);
        BankStore.writeOperation(v -> {
            users.updatePassword(actor.id(), hash);
            audit.record(actor.id(), actor.username(), actor.role(), "ALTERAR_PASSWORD", "USER",
                    String.valueOf(actor.id()), "CONCLUIDA", null);
            return null;
        });
    }

    private boolean passwordOk(String password, String stored) throws Exception {
        if (password == null || stored == null) return false;
        return Passwords.verify(password, stored);
    }
}
