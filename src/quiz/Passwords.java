package quiz;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Passwords {
    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + derive(password, salt);
    }
    private static String derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            throw new IllegalStateException("Password protection is unavailable.", e);
        } finally {
            spec.clearPassword();
        }
    }
    public static boolean matches(String password, String stored) {
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        return MessageDigest.isEqual(Base64.getDecoder().decode(parts[1]),
                Base64.getDecoder().decode(derive(password, salt)));
    }
}
