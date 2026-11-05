package com.foodloop.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonationCodeGeneratorTest {

    @Test
    void codeHasExpectedFormatAndFitsColumn() {
        String code = DonationCodeGenerator.generate(2026);
        assertTrue(code.matches("^FL-2026-\\d{6}$"), code);
        assertTrue(code.length() <= 20);
    }

    @Test
    void codesVary() {
        String a = DonationCodeGenerator.generate(2026);
        boolean different = false;
        for (int i = 0; i < 20 && !different; i++) {
            different = !a.equals(DonationCodeGenerator.generate(2026));
        }
        assertTrue(different);
        assertEquals(14, a.length());
    }
}