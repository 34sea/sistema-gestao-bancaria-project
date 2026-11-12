package utils;

import exceptions.BusinessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validações centrais do sistema (usadas pelos serviços — backend — e pelas
 * telas — frontend): palavras-passe fortes, números de celular de Moçambique,
 * e-mails, utilizadores, BI, nomes e datas. Todas as mensagens são específicas
 * e indicam exactamente o que deve ser corrigido.
 */
public final class Validation {

    /** Limites gerais dos campos de texto. */
    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 120;
    public static final int SHORT_TEXT_MAX = 120;
    public static final java.math.BigDecimal MAX_AMOUNT = new java.math.BigDecimal("10000000000"); // 10 mil milhões

    private static final Set<String> PHONE_PREFIXES = Set.of("82", "83", "84", "85", "86", "87", "88");
    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    private Validation() {}

    // ---------------------------------------------------------- palavras-passe

    /** Requisitos ainda não cumpridos (lista vazia = palavra-passe forte). */
    public static List<String> passwordIssues(String p) {
        List<String> issues = new ArrayList<>();
        if (p == null || p.isEmpty()) {
            issues.add("é obrigatória");
            return issues;
        }
        if (p.length() < 8) issues.add("mínimo de 8 caracteres (tem " + p.length() + ")");
        if (p.chars().noneMatch(Character::isUpperCase)) issues.add("pelo menos uma letra maiúscula (A–Z)");
        if (p.chars().noneMatch(Character::isLowerCase)) issues.add("pelo menos uma letra minúscula (a–z)");
        if (p.chars().noneMatch(Character::isDigit)) issues.add("pelo menos um número (0–9)");
        if (p.chars().noneMatch(c -> !Character.isLetterOrDigit(c))) issues.add("pelo menos um carácter especial (ex.: !, @, #)");
        return issues;
    }

    /**
     * Valida a força da palavra-passe; devolve null quando válida ou a
     * mensagem com TODOS os requisitos em falta.
     */
    public static String passwordError(String p) {
        List<String> issues = passwordIssues(p);
        return issues.isEmpty() ? null
                : "A palavra-passe não cumpre os requisitos: " + String.join("; ", issues) + ".";
    }

    public static void checkPassword(String p) throws BusinessException {
        String err = passwordError(p);
        if (err != null) throw new BusinessException(err);
    }

    /** Confirmação de palavra-passe: devem coincidir. */
    public static void checkPasswordPair(String password, String confirm) throws BusinessException {
        checkPassword(password);
        if (!password.equals(confirm)) {
            throw new BusinessException("A confirmação da palavra-passe não coincide com a palavra-passe.");
        }
    }

    /** Lista legível dos requisitos para mostrar junto ao campo. */
    public static String passwordRequirements() {
        return "Mínimo 8 caracteres, com maiúscula, minúscula, número e carácter especial.";
    }

    // ---------------------------------------------------------- celular (Moçambique)

    /**
     * Normaliza e valida um número de celular moçambicano:
     * aceita "+258 84 111 0001", "258841110001", "084 111 0001" ou "841110001";
     * devolve os 9 dígitos do número nacional, ou null se vazio.
     */
    public static String normalizePhone(String raw) throws BusinessException {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;

        // remove espaços, traços, pontos e parênteses
        String clean = t.replaceAll("[\\s.\\-()]", "");
        // código internacional +258 / 258
        if (clean.startsWith("+258")) clean = clean.substring(4);
        else if (clean.startsWith("258")) clean = clean.substring(3);
        // tronco local 0 opcional (084...)
        if (clean.startsWith("0")) clean = clean.substring(1);

        if (!clean.matches("\\d+")) {
            throw new BusinessException("O número de celular não pode conter letras ou símbolos (só dígitos, "
                    + "opcionalmente o código +258).");
        }
        if (clean.length() != 9) {
            throw new BusinessException("O número de celular deve ter 9 dígitos"
                    + (t.contains("258") ? " além do código internacional +258" : "")
                    + " (ex.: 84 111 0001) — recebeu " + clean.length() + " dígito(s).");
        }
        if (!PHONE_PREFIXES.contains(clean.substring(0, 2))) {
            throw new BusinessException("Prefixo de celular inválido: deve começar por 82, 83, 84, 85, 86, 87 ou 88.");
        }
        return clean;
    }

    /** Como normalizePhone, mas exigindo o número (campo obrigatório). */
    public static String requirePhone(String raw) throws BusinessException {
        String p = normalizePhone(raw);
        if (p == null) throw new BusinessException("O número de celular é obrigatório.");
        return p;
    }

    // ---------------------------------------------------------- e-mail

    /** E-mail em minúsculas e validado; null quando opcional e vazio. */
    public static String normalizeEmail(String raw) throws BusinessException {
        if (raw == null || raw.isBlank()) return null;
        String e = raw.strip();
        if (!e.matches(EMAIL_REGEX)) {
            throw new BusinessException("E-mail inválido — use o formato nome@dominio.com (ex.: joao@mail.com).");
        }
        return e.toLowerCase();
    }

    // ---------------------------------------------------------- textos comuns

    /** Nome próprio/completo: obrigatório, 3–120 caracteres, apenas letras e separadores. */
    public static String requireName(String raw, String label) throws BusinessException {
        if (raw == null || raw.strip().isEmpty()) throw new BusinessException(label + " é obrigatório.");
        String v = raw.strip();
        if (v.length() < NAME_MIN) throw new BusinessException(label + " deve ter pelo menos " + NAME_MIN + " caracteres.");
        if (v.length() > NAME_MAX) throw new BusinessException(label + " não pode exceder " + NAME_MAX + " caracteres.");
        if (!v.matches("[\\p{L} .\\-'’]+")) {
            throw new BusinessException(label + " só pode conter letras, espaços, apóstrofos, travessões e pontos.");
        }
        return v;
    }

    /** Texto simples (endereço, cidade, cargo, departamento): opcional, com limite de tamanho. */
    public static String optionalText(String raw, String label, int max) throws BusinessException {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.strip();
        if (v.length() > max) throw new BusinessException(label + " não pode exceder " + max + " caracteres.");
        return v;
    }

    /** Nome de utilizador: 4–30, letras/números/ponto/hífen/sublinhado. */
    public static String username(String raw) throws BusinessException {
        if (raw == null || raw.isBlank()) throw new BusinessException("O nome de utilizador é obrigatório.");
        String v = raw.strip();
        if (!v.matches("[A-Za-z0-9._\\-]{4,30}")) {
            throw new BusinessException("O nome de utilizador deve ter 4–30 caracteres "
                    + "(letras, números, ponto, hífen ou sublinhado).");
        }
        return v;
    }

    /** Documento de identificação (BI): 8–20 caracteres alfanuméricos com travessões. */
    public static String document(String raw) throws BusinessException {
        if (raw == null || raw.strip().isEmpty()) {
            throw new BusinessException("O número de identificação (BI) é obrigatório.");
        }
        String v = raw.strip().toUpperCase();
        if (!v.matches("[A-Z0-9\\-]{8,20}")) {
            throw new BusinessException("Número de identificação inválido — deve ter 8–20 caracteres "
                    + "alfanuméricos (ex.: BI001100777AA-DM).");
        }
        return v;
    }

    /** Data de nascimento: válida e dentro dos limites humanos. */
    public static void birthDate(LocalDate d) throws BusinessException {
        if (d == null) return; // opcional
        if (d.isAfter(LocalDate.now())) {
            throw new BusinessException("A data de nascimento não pode ser no futuro.");
        }
        if (d.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new BusinessException("A data de nascimento deve ser depois de 1900.");
        }
        if (d.plusYears(120).isBefore(LocalDate.now())) {
            throw new BusinessException("A data de nascimento parece incorrecta (mais de 120 anos).");
        }
    }
}
