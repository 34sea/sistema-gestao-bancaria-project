package exceptions;

/** Operação não autorizada para o perfil em causa (RBAC). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
