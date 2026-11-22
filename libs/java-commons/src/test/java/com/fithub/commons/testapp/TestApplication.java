package com.fithub.commons.testapp;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Aplicação mínima que usa a java-commons como um serviço usaria. */
@SpringBootApplication
public class TestApplication {

    static final String ADMIN_TOKEN = "token-admin";
    static final String CLIENT_TOKEN = "token-client";

    /** Decodificador falso: só os dois tokens de teste são válidos; qualquer outro é recusado. */
    @Bean
    JwtDecoder jwtDecoder() {
        return token -> switch (token) {
            case ADMIN_TOKEN -> jwt(token, "usuario-admin", List.of("admin"));
            case CLIENT_TOKEN -> jwt(token, "usuario-cliente", List.of("client"));
            default -> throw new BadJwtException("token inválido");
        };
    }

    private static Jwt jwt(String token, String subject, List<String> roles) {
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    record CreateRequest(@NotBlank(message = "é obrigatório") String name) {
    }

    @RestController
    static class TestController {

        private static final Logger log = LoggerFactory.getLogger(TestController.class);

        @GetMapping("/secure")
        Map<String, String> secure(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("subject", jwt.getSubject());
        }

        @GetMapping("/admin")
        @PreAuthorize("hasRole('ADMIN')")
        Map<String, String> admin() {
            return Map.of("ok", "true");
        }

        @GetMapping("/boom")
        Map<String, String> boom() {
            throw new IllegalStateException("detalhe interno que não pode vazar");
        }

        @PostMapping("/items")
        Map<String, String> create(@Valid @RequestBody CreateRequest request) {
            return Map.of("name", request.name());
        }

        @GetMapping("/log")
        Map<String, String> logLine() {
            log.info("linha de log de teste da java-commons");
            return Map.of("ok", "true");
        }
    }
}
