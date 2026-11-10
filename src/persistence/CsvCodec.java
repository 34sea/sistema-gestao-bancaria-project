package persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** RFC-4180 simplificado: campos entre "..." com escape \" e \n internos. */
public final class CsvCodec {
    private CsvCodec() {}

    /** Codifica uma linha de valores (sem quebra de linha final). */
    public static String line(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            String v = values.get(i) == null ? "" : values.get(i);
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                v = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
                sb.append('"').append(v).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /** Decodifica uma linha em lista de valores. */
    public static List<String> parse(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < line.length()) {
                    char n = line.charAt(++i);
                    switch (n) {
                        case 'n' -> cur.append('\n');
                        case 'r' -> cur.append('\r');
                        default -> { cur.append(n); }
                    }
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == ',' ) {
                out.add(cur.toString());
                cur.setLength(0);
            } else if (c == '"' && cur.isEmpty()) {
                quoted = true;
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ------------------------------------------------------------ datas

    public static String date(LocalDate d)  { return d == null ? "" : d.toString(); }
    public static String ts(LocalDateTime t){ return t == null ? "" : t.toString(); }

    public static LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim().substring(0, 10)); } catch (DateTimeParseException e) { return null; }
    }

    public static LocalDateTime ts(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String v = s.trim();
            if (v.length() == 10) return LocalDate.parse(v).atStartOfDay();
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException e) { return null; }
    }
}
