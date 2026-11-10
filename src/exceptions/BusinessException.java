package exceptions;

/** Erro de regra de negócio (mensagem destinada ao utilizador). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
    public BusinessException(String message, Throwable cause) { super(message, cause); }
}
