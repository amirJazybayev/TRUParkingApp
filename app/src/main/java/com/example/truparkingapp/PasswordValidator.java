package com.example.truparkingapp;

/**
 * Password rules from the requirements: at least 8 characters (at most 64),
 * one capital letter, one number and one special symbol.
 * Plain Java so the sign-up checklist and the unit tests share it.
 */
public final class PasswordValidator {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private PasswordValidator() {
    }

    public static boolean hasValidLength(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH;
    }

    public static boolean hasUppercase(String password) {
        if (password == null) return false;
        for (int i = 0; i < password.length(); i++) {
            if (Character.isUpperCase(password.charAt(i))) return true;
        }
        return false;
    }

    public static boolean hasDigit(String password) {
        if (password == null) return false;
        for (int i = 0; i < password.length(); i++) {
            if (Character.isDigit(password.charAt(i))) return true;
        }
        return false;
    }

    /** A symbol is anything that is not a letter, a digit or a space. */
    public static boolean hasSymbol(String password) {
        if (password == null) return false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) return true;
        }
        return false;
    }

    public static boolean isValid(String password) {
        return hasValidLength(password)
                && hasUppercase(password)
                && hasDigit(password)
                && hasSymbol(password);
    }
}
