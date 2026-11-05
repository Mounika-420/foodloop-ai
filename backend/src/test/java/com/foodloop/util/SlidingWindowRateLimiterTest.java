package com.foodloop.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {
    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        SlidingWindowRateLimiter r = new SlidingWindowRateLimiter(3, 1000);
        assertTrue(r.tryAcquire("ip", 0));
        assertTrue(r.tryAcquire("ip", 100));
        assertTrue(r.tryAcquire("ip", 200));
        assertFalse(r.tryAcquire("ip", 300));
        assertFalse(r.tryAcquire("ip", 999));
        assertTrue(r.tryAcquire("ip", 1001));
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowRateLimiter r = new SlidingWindowRateLimiter(1, 1000);
        assertTrue(r.tryAcquire("a", 0));
        assertFalse(r.tryAcquire("a", 1));
        assertTrue(r.tryAcquire("b", 1));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRateLimiter(0, 1000));
    }
}
