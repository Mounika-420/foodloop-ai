package com.foodloop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloop.dto.ApiError;
import com.foodloop.util.SlidingWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/** Per-IP, per-endpoint throttle on the unauthenticated auth endpoints (brute-force protection). */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password");

    private final SlidingWindowRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper, int maxPerMinute) {
        this.objectMapper = objectMapper;
        this.limiter = new SlidingWindowRateLimiter(maxPerMinute, 60_000);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        if (limiter.tryAcquire(key, System.currentTimeMillis())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(limiter.windowSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(LocalDateTime.now().toString(), 429,
                "Too Many Requests", "Too many requests. Please try again in a minute.", request.getRequestURI(), null));
    }
}
