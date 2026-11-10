package utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Palavras-passe: PBKDF2WithHmacSHA256, 210 000 iterações, 256 bits,
 * salt aleatória de 16 bytes. Armazenado como base64(salt)-base64(hash).
 */
public final class Passwords {
    private Passwords() {}

    public static String hash(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] h = secretKeyFactory().generateSecret(spec(password, salt)).getEncoded();
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(h);
    }

    public static boolean verify(String password, String stored) throws Exception {
        String[] a = stored.split(":");
        if (a.length != 2) return false;
        byte[] salt = Base64.getDecoder().decode(a[0]);
        byte[] expected = Base64.getDecoder().decode(a[1]);
        byte[] actual = secretKeyFactory().generateSecret(spec(password, salt)).getEncoded();
        return MessageDigest.isEqual(expected, actual);
    }

    private static SecretKeyFactory secretKeyFactory() throws Exception {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    }

    private static PBEKeySpec spec(String password, byte[] salt) {
        return new PBEKeySpec(password.toCharArray(), salt, 210_000, 256);
    }
}
