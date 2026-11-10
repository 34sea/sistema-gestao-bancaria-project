package models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Conta bancária da simulação. */
public class Account {
    public long id;
    public String accountNumber;
    public long clientId;
    public String clientName;
    public String accountTypeCode;   // CORRENTE | POUPANCA
    public String accountTypeName;
    public BigDecimal balance = BigDecimal.ZERO;
    public String currency = "MZN";
    public String status;            // ACTIVA | BLOQUEADA | ENCERRADA
    public BigDecimal interestAccrued = BigDecimal.ZERO;
    public LocalDateTime openedAt;
    public LocalDateTime lastMovementAt;
}
