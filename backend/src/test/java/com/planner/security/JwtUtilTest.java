package com.planner.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void createAndParse() {
        JwtUtil jwt = new JwtUtil(SECRET, 1);
        assertEquals(Long.valueOf(42L), jwt.parse(jwt.create(42L)));
    }

    @Test
    void garbageTokenReturnsNull() {
        assertNull(new JwtUtil(SECRET, 1).parse("not-a-jwt"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = new JwtUtil("abcdefghijklmnopqrstuvwxyz012345", 1).create(1L);
        assertNull(new JwtUtil(SECRET, 1).parse(token));
    }

    @Test
    void expiredTokenReturnsNull() {
        JwtUtil jwt = new JwtUtil(SECRET, -1);
        assertNull(jwt.parse(jwt.create(1L)));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("short", 1));
    }
}
