package com.fithub.commons.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import tools.jackson.databind.json.JsonMapper;

/**
 * Cada serviço é um resource server do Keycloak (ADR-004): API sem sessão, todo caminho fora de
 * {@code fithub.security.public-paths} exige JWT válido, papéis do realm viram {@code ROLE_*} e
 * {@code @PreAuthorize} fica habilitado. Roda antes das autoconfigurações de segurança do Spring Boot
 * para que a cadeia padrão delas não seja criada; um serviço pode declarar a própria cadeia.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(FithubSecurityProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class FithubSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailsSecurityHandler problemDetailsSecurityHandler(JsonMapper jsonMapper) {
        return new ProblemDetailsSecurityHandler(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain fithubSecurityFilterChain(HttpSecurity http, FithubSecurityProperties properties,
            ProblemDetailsSecurityHandler problemHandler) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        authenticationConverter.setPrincipalClaimName("sub");

        String[] publicPaths = properties.publicPaths().toArray(String[]::new);
        http
                // API stateless com Bearer token: sem cookie de sessão, CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }
}
