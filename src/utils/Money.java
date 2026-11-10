package utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatação monetária da simulação: sempre MZN, "25.000,00 MZN". */
public final class Money {
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(Money::build);
    private Money() {}

    private static DecimalFormat build() {
        DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("pt", "PT"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        DecimalFormat f = new DecimalFormat("#,##0.00", s);
        f.setParseBigDecimal(true);
        return f;
    }

    /** Formato apresentação: 25.000,00 MZN */
    public static String format(BigDecimal v) {
        return FORMAT.get().format(v == null ? BigDecimal.ZERO : v) + " MZN";
    }

    /** Formato numérico apenas: 25.000,00 */
    public static String number(BigDecimal v) {
        return FORMAT.get().format(v == null ? BigDecimal.ZERO : v);
    }

    /** Analisa a entrada do utilizador (aceita 1.234,56 e 1234.56). */
    public static BigDecimal parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Indique um valor.");
        String t = raw.trim().replace(" ", "").replace("MZN", "");
        if (t.contains(",")) t = t.replace(".", "").replace(',', '.');
        try {
            BigDecimal v = new BigDecimal(t);
            if (v.scale() > 2) {
                throw new IllegalArgumentException("O valor monetário não pode ter mais de duas casas decimais.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor monetário inválido: " + raw);
        }
    }

    public static boolean isValidAmount(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0 && v.scale() <= 2;
    }

    /** Arredonda para baixo com 2 casas decimais (escala padrão dos saldos). */
    public static BigDecimal scaled(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.DOWN);
    }
}
