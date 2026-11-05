package com.foodloop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/** Creates and validates signed JWT access tokens (HMAC-SHA). The secret comes from the environment, never from code. */
@Service
public class JwtService {
    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiry-minutes:15}") long expiryMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT secret missing or too short. Set JWT_SECRET (or app.jwt.secret) to at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    public String generateToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException if the token is malformed, tampered with or expired */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirySeconds() {
        return expiryMinutes * 60;
    }
}
