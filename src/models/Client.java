package models;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cliente da instituição (pessoa). */
public class Client {
    public long id;
    public String fullName, documentNumber, gender, address, city, phone, email, status;
    public LocalDate birthDate;
    public LocalDateTime createdAt, updatedAt;
    public String portalUsername;
}
