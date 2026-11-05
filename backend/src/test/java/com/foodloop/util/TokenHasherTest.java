package com.foodloop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class TokenHasherTest {
    @Test
    void matchesKnownSha256Vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenHasher.sha256Hex("abc"));
    }

    @Test
    void differentInputsGiveDifferentHashes() {
        assertNotEquals(TokenHasher.sha256Hex("a"), TokenHasher.sha256Hex("b"));
    }
}
