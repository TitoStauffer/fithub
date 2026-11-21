package com.fithub.identity.infrastructure.security;

import com.fithub.identity.domain.valueobject.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
            "fithub-secret-key-for-development-only-min-32-chars",
            900000,      // 15 min access token
            604800000    // 7 dias refresh token
        );
    }

    @Test
    void shouldGenerateValidAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, UserRole.PATIENT);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldGenerateValidRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateRefreshToken(userId);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldValidateCorrectToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, UserRole.PATIENT);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    void shouldExtractUserIdFromToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, UserRole.PATIENT);

        UUID extractedUserId = jwtTokenProvider.extractUserId(token);
        assertEquals(userId, extractedUserId);
    }

    @Test
    void shouldExtractRoleFromToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, UserRole.PT);

        String role = jwtTokenProvider.extractRole(token);
        assertEquals("PT", role);
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentUsers() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        String token1 = jwtTokenProvider.generateAccessToken(userId1, UserRole.PATIENT);
        String token2 = jwtTokenProvider.generateAccessToken(userId2, UserRole.PATIENT);

        assertNotEquals(token1, token2);
    }

    @Test
    void shouldIdentifyAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, UserRole.PATIENT);

        String tokenType = jwtTokenProvider.extractTokenType(token);
        assertEquals("access", tokenType);
    }

    @Test
    void shouldIdentifyRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateRefreshToken(userId);

        String tokenType = jwtTokenProvider.extractTokenType(token);
        assertEquals("refresh", tokenType);
    }
}
