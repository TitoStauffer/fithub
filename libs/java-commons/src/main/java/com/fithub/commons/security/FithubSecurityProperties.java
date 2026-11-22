package com.fithub.commons.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de segurança comum dos serviços ({@code fithub.security.*}).
 * O issuer e o JWKS do Keycloak vêm das propriedades padrão do Spring Boot
 * ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri} ou {@code jwk-set-uri}).
 *
 * @param publicPaths caminhos acessíveis sem token; o resto exige autenticação.
 */
@ConfigurationProperties("fithub.security")
public record FithubSecurityProperties(List<String> publicPaths) {

    /** Saúde para o orquestrador e documentação OpenAPI; nada de dado de cliente. */
    public static final List<String> DEFAULT_PUBLIC_PATHS = List.of(
            "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");

    public FithubSecurityProperties {
        publicPaths = publicPaths == null ? DEFAULT_PUBLIC_PATHS : List.copyOf(publicPaths);
    }
}
