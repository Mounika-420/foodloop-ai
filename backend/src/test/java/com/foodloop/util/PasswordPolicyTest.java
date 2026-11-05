package com.foodloop.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void acceptsStrongPassword() {
        assertTrue(PasswordPolicy.isStrong("Password@123"));
    }

    @Test
    void rejectsMissingCharacterClasses() {
        assertFalse(PasswordPolicy.isStrong("password@123"));
        assertFalse(PasswordPolicy.isStrong("PASSWORD@123"));
        assertFalse(PasswordPolicy.isStrong("Password@abc"));
        assertFalse(PasswordPolicy.isStrong("Password123"));
    }

    @Test
    void rejectsBadLengthSpacesAndNull() {
        assertFalse(PasswordPolicy.isStrong("Pass@1"));
        assertFalse(PasswordPolicy.isStrong("Pass word@123"));
        assertFalse(PasswordPolicy.isStrong("A@1a".repeat(19)));
        assertFalse(PasswordPolicy.isStrong(null));
    }
}
