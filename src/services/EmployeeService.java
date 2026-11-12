package services;

import exceptions.BusinessException;
import models.User;
import persistence.BankStore;
import persistence.BankStore.UserRow;
import repositories.AuditRepository;
import repositories.UserRepository;
import utils.Passwords;
import utils.Validation;

import java.util.List;

/**
 * Gestão de funcionários e utilizadores administrativos (administrador):
 * criação, activar/desactivar e repor palavra-passe.
 */
public class EmployeeService {

    private final UserRepository users = new UserRepository();
    private final AuditRepository audits = new AuditRepository();

    /** Criação de funcionário (perfil FUNCIONARIO). */
    public long create(User actor, String username, String fullName, String email,
                       String password, String position, String department) throws Exception {
        requireAdmin(actor);
        String user = validate(username, fullName, email, password, null);
        if (users.existsUsername(user)) {
            throw new BusinessException("Já existe um utilizador com este nome de acesso.");
        }
        String hash = Passwords.hash(password);
        String normEmail = Validation.normalizeEmail(email);
        String pos = Validation.optionalText(position, "O cargo", 60);
        String dep = Validation.optionalText(department, "O departamento", 60);
        return BankStore.writeOperation(v -> {
            long userId = users.create(user, fullName.strip(), normEmail,
                    hash, "FUNCIONARIO", null,
                    pos == null ? "FUNCIONARIO" : pos, dep);
            audits.record(actor.id(), actor.username(), actor.role(), "CRIAR_FUNCIONARIO",
                    "USER", String.valueOf(userId), "CONCLUIDA", username);
            return userId;
        });
    }

    /** Criação directa de utilizador administrativo (perfil ADMIN). */
    public long createAdmin(User actor, String username, String fullName, String email, String password) throws Exception {
        requireAdmin(actor);
        String user = validate(username, fullName, email, password, null);
        if (users.existsUsername(user)) {
            throw new BusinessException("Já existe um utilizador com este nome de acesso.");
        }
        String hash = Passwords.hash(password);
        String normEmail = Validation.normalizeEmail(email);
        return BankStore.writeOperation(v -> {
            long userId = users.create(user, fullName.strip(), normEmail,
                    hash, "ADMIN", null, "ADMINISTRADOR", "GERAL");
            audits.record(actor.id(), actor.username(), actor.role(), "CRIAR_USER",
                    "USER", String.valueOf(userId), "CONCLUIDA", username);
            return userId;
        });
    }

    /** Activar / desactivar utilizador administrativo (perfil FUNCIONARIO ou ADMIN). */
    public void setActive(User actor, long userId, boolean active) throws Exception {
        requireAdmin(actor);
        BankStore.writeOperation(v -> {
            UserRow u = users.rawById(userId);
            if (u == null || "CLIENTE".equals(u.role)) {
                throw new BusinessException("Utilizador administrativo não encontrado.");
            }
            boolean sameCurrentUser = userId == actor.id();
            if (!active && sameCurrentUser) {
                throw new BusinessException("Não pode desactivar a sua própria conta.");
            }
            users.setActive(userId, active);
            audits.record(actor.id(), actor.username(), actor.role(),
                    active ? "ACTIVAR_USER" : "DESACTIVAR_USER", "USER",
                    String.valueOf(userId), "CONCLUIDA", null);
            return null;
        });
    }

    public void resetPassword(User actor, long userId, String newPassword) throws Exception {
        requireAdmin(actor);
        Validation.checkPassword(newPassword);
        String hash = Passwords.hash(newPassword);
        BankStore.writeOperation(v -> {
            UserRow u = users.rawById(userId);
            if (u == null || "CLIENTE".equals(u.role)) {
                throw new BusinessException("Funcionário não encontrado.");
            }
            users.updatePassword(userId, hash);
            audits.record(actor.id(), actor.username(), actor.role(), "REPOR_PASSWORD",
                    "USER", String.valueOf(userId), "CONCLUIDA", null);
            return null;
        });
    }

    /** Lista de funcionários: [empId, userId, username, nome, email, estado, cargo, departamento]. */
    public List<String[]> list(String search) throws Exception {
        return users.listRows("FUNCIONARIO", search);
    }

    /** Validação comum de credenciais; devolve o nome de utilizador normalizado. */
    private String validate(String username, String fullName, String email, String password, String confirm)
            throws BusinessException {
        String user = Validation.username(username);
        if (fullName == null || fullName.isBlank()) throw new BusinessException("O nome é obrigatório.");
        Validation.requireName(fullName, "O nome");
        Validation.normalizeEmail(email); // null quando vazio; erro quando formato inválido
        Validation.checkPassword(password);
        if (confirm != null && !confirm.equals(password)) {
            throw new BusinessException("A confirmação da palavra-passe não coincide com a palavra-passe.");
        }
        return user;
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new BusinessException("Somente o administrador gere funcionários.");
        }
    }

}
