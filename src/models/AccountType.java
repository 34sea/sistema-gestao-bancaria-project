package models;

import java.math.BigDecimal;
import java.util.List;

/** Tipo de conta da simulação (configuração fixa, correspondente aos dados semeados). */
public record AccountType(String code, String name, BigDecimal interestRate,
                          boolean allowsWithdrawal, boolean allowsTransfer) {

    public static final List<AccountType> TYPES = List.of(
            new AccountType("CORRENTE", "Conta Corrente", new BigDecimal("0.0000"), true, true),
            new AccountType("POUPANCA", "Conta Poupança", new BigDecimal("0.0500"), true, true));

    public static AccountType byCode(String code) {
        for (AccountType t : TYPES) if (t.code().equals(code)) return t;
        return null;
    }

    /** Percentagem de juros em texto (ex.: 5%). */
    public String ratePercent() {
        return interestRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%";
    }
}
