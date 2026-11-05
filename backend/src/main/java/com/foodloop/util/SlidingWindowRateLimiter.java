package com.foodloop.util;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window rate limiter (single instance deployment). */
public class SlidingWindowRateLimiter {
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxRequests, long windowMillis) {
        if (maxRequests <= 0 || windowMillis <= 0) throw new IllegalArgumentException("limits must be positive");
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    /** @return true if the request is allowed, false if the key exceeded the limit. */
    public boolean tryAcquire(String key, long nowMillis) {
        if (hits.size() > 10_000) cleanup(nowMillis);
        ArrayDeque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            long cutoff = nowMillis - windowMillis;
            while (!q.isEmpty() && q.peekFirst() <= cutoff) q.pollFirst();
            if (q.size() >= maxRequests) return false;
            q.addLast(nowMillis);
            return true;
        }
    }

    public long windowSeconds() {
        return Math.max(1, windowMillis / 1000);
    }

    private void cleanup(long nowMillis) {
        long cutoff = nowMillis - windowMillis;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() <= cutoff;
            }
        });
    }
}
