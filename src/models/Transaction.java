package models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Movimento registado (linha do histórico). */
public record Transaction(
        long id, String code, long accountId, String accountNumber, Long counterpartyAccountId,
        String counterpartyAccountNumber, String counterpartyName, Long transferId, String transferCode,
        String type, BigDecimal amount, BigDecimal before, BigDecimal after, String status,
        String description, String performedByName, LocalDateTime createdAt) {
}
