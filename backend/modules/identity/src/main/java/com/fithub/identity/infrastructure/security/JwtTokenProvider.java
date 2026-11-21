package com.fithub.identity.infrastructure.security;

import com.fithub.identity.domain.valueobject.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Provedor de JWT (tokens de autenticação).
 * Gera e valida tokens com dois tipos:
 * - Access Token (15 min): contém userId + role
 * - Refresh Token (7 dias): contém apenas userId
 */
@Component
public class JwtTokenProvider {
    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;  // 15 min
    private final long refreshTokenExpirationMs; // 7 dias

    public JwtTokenProvider(
        @Value("${jwt.secret}") String jwtSecret,
        @Value("${jwt.access-token-expiration:900000}") long accessTokenExpirationMs,
        @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs
    ) {
        // CRÍTICO: Secret key MUST ser configurada. Sem default em produção.
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "FATAL: JWT_SECRET environment variable must be set and >= 32 characters. " +
                "Security cannot proceed without proper secret configuration."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Gera Access Token (curta duração: 15 min).
     * Contém userId + role para autorização rápida.
     */
    public String generateAccessToken(UUID userId, UserRole role) {
        Instant now = Instant.now();
        Instant expiryTime = now.plusMillis(accessTokenExpirationMs);

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("role", role.name())
            .claim("type", "access")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiryTime))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Gera Refresh Token (longa duração: 7 dias).
     * Contém apenas userId; role é refetchado do banco ao renovar.
     */
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiryTime = now.plusMillis(refreshTokenExpirationMs);

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("type", "refresh")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiryTime))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Valida a integridade e expiração do token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extrai userId (subject) do token.
     */
    public UUID extractUserId(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extrai role do token.
     */
    public String extractRole(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
        return (String) claims.get("role");
    }

    /**
     * Extrai o tipo do token (access ou refresh).
     */
    public String extractTokenType(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
        return (String) claims.getOrDefault("type", "access");
    }
}
