package models;

/** Utilizador autenticado do sistema (sem dados sensíveis). */
public record User(long id, String username, String fullName, String role, Long clientId, String email) {
    public boolean isAdmin()        { return "ADMIN".equals(role); }
    public boolean isEmployee()     { return "FUNCIONARIO".equals(role); }
    public boolean isClient()       { return "CLIENTE".equals(role); }
    public boolean hasStaffRights() { return isAdmin() || isEmployee(); }
    public long clientIdOrZero()    { return clientId == null ? 0L : clientId; }
}
