package com.example.truparkingapp;

import java.util.Locale;

/**
 * Username rules: 3 to 20 characters, letters, numbers and underscore only.
 * Uniqueness ignores upper/lower case, so "Amir" and "amir" share one key.
 */
public final class UsernameValidator {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;

    private UsernameValidator() {
    }

    public static boolean isValid(String username) {
        if (username == null) return false;
        String name = username.trim();
        if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!allowed) return false;
        }
        return true;
    }

    /** The document id used in the "usernames" collection. */
    public static String toKey(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
