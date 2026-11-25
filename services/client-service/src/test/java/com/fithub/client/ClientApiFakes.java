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

    /**
     * Tokens de teste: {@code Bearer conta-<uuid>} vira um JWT de cliente com esse {@code sub};
     * {@code personal-<uuid>} e {@code nutri-<uuid>} trazem também o papel de profissional, e {@code sempapel-<uuid>},
     * nenhum papel. Todos trazem o e-mail {@code <uuid>@teste.fithub}. O resto é recusado.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        Map<String, List<String>> rolesByPrefix = Map.of(
                "conta-", List.of("client"),
                "personal-", List.of("client", "personal"),
                "nutri-", List.of("client", "nutritionist"),
                "sempapel-", List.<String>of());
        return token -> rolesByPrefix.entrySet().stream()
                .filter(entry -> token.startsWith(entry.getKey()))
                .findFirst()
                .map(entry -> Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token.substring(entry.getKey().length()))
                        .claim("realm_access", Map.of("roles", entry.getValue()))
                        .claim("email", token.substring(entry.getKey().length()) + "@teste.fithub")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build())
                .orElseThrow(() -> new BadJwtException("token inválido"));
    }
}
