package com.iskolar.util;

import org.mindrot.jbcrypt.BCrypt;

/** jBCrypt wrappers — the only place password hashing lives. */
public final class PasswordUtils {

    private static final int BCRYPT_ROUNDS = 12;

    private PasswordUtils() {
    }

    public static String hash(String plainText) {
        return BCrypt.hashpw(plainText, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    public static boolean verify(String plainText, String hash) {
        if (plainText == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainText, hash);
        } catch (IllegalArgumentException e) {
            return false; // malformed stored hash
        }
    }
}
