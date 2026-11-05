package com.foodloop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "UnitTestSecretKeyThatIsAtLeast32Characters!";
    private static final String OTHER_SECRET = "AnotherDifferentSecretKeyWith32PlusChars!!";

    @Test
    void generatesAndParsesToken() {
        JwtService svc = new JwtService(SECRET, 15);
        Claims c = svc.parse(svc.generateToken(5L, "a@b.com", "DONOR"));
        assertEquals("5", c.getSubject());
        assertEquals("a@b.com", c.get("email", String.class));
        assertEquals("DONOR", c.get("role", String.class));
        assertNotNull(c.getId());
        assertEquals(900, svc.getExpirySeconds());
    }

    @Test
    void eachTokenHasUniqueId() {
        JwtService svc = new JwtService(SECRET, 15);
        String id1 = svc.parse(svc.generateToken(1L, "a@b.com", "NGO")).getId();
        String id2 = svc.parse(svc.generateToken(1L, "a@b.com", "NGO")).getId();
        assertNotEquals(id1, id2);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String foreign = new JwtService(OTHER_SECRET, 15).generateToken(1L, "a@b.com", "ADMIN");
        assertThrows(JwtException.class, () -> new JwtService(SECRET, 15).parse(foreign));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService svc = new JwtService(SECRET, -1);      // already expired when created
        String token = svc.generateToken(1L, "a@b.com", "NGO");
        assertThrows(JwtException.class, () -> svc.parse(token));
    }

    @Test
    void rejectsGarbageToken() {
        assertThrows(JwtException.class, () -> new JwtService(SECRET, 15).parse("not.a.jwt"));
    }

    @Test
    void refusesToStartWithShortOrMissingSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 15));
        assertThrows(IllegalStateException.class, () -> new JwtService("", 15));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 15));
    }
}
