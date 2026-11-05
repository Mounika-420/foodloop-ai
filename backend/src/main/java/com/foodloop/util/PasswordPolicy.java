package com.foodloop.util;

/** Password rules used at registration, reset and change-password. Pure Java, easy to unit test. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes, so longer passwords are rejected to avoid silent truncation. */
    public static final int MAX_LENGTH = 72;
    public static final String MESSAGE =
            "Password must be 8-72 characters with an uppercase letter, a lowercase letter, a digit and a special character, and no spaces";

    private PasswordPolicy() {}

    public static boolean isStrong(String p) {
        if (p == null || p.length() < MIN_LENGTH || p.length() > MAX_LENGTH) return false;
        boolean upper = false, lower = false, digit = false, special = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (Character.isWhitespace(c)) return false;
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true;
        }
        return upper && lower && digit && special;
    }
}
