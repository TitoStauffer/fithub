package com.fithub.client;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Relógio fixo e tokens de teste dos testes de API do client-service (um contexto Spring só para todos). */
@TestConfiguration
class ClientApiFakes {

    /** Hoje, no teste, é 23/11/2026. */
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
    }

    /** Token de teste: {@code Bearer conta-<uuid>} vira um JWT com esse {@code sub}; o resto é recusado. */
    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            if (!token.startsWith("conta-")) {
                throw new BadJwtException("token inválido");
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token.substring("conta-".length()))
                    .claim("realm_access", Map.of("roles", List.of("client")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        };
    }
}
