package utils;

import java.util.UUID;

/** Geradores de códigos legíveis para operações (simulação). */
public final class Ids {
    private Ids() {}
    public static String transactionCode() { return "TX-" + shortCode(); }
    public static String transferCode()    { return "TR-" + shortCode(); }
    public static String sessionToken()    { return UUID.randomUUID().toString(); }

    private static String shortCode() {
        String t = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return t.substring(0, 4) + "-" + t.substring(4, 8) + "-" + t.substring(8, 12);
    }
}
