package com.fithub.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter converter = new KeycloakRolesConverter();

    @Test
    void realmRolesBecomeUppercaseRoleAuthorities() {
        assertThat(authorities(Map.of("realm_access", Map.of("roles", List.of("personal", "nutritionist", "personal")))))
                .containsExactly("ROLE_PERSONAL", "ROLE_NUTRITIONIST");
    }

    @Test
    void missingOrMalformedClaimGrantsNothing() {
        assertThat(authorities(Map.of("scope", "openid"))).isEmpty();
        assertThat(authorities(Map.of("realm_access", "admin"))).isEmpty();
        assertThat(authorities(Map.of("realm_access", Map.of("roles", "admin")))).isEmpty();
        assertThat(authorities(Map.of("realm_access", Map.of("roles", List.of(1, " ", "client"))))).containsExactly("ROLE_CLIENT");
    }

    @Test
    void clientRolesOutsideTheRealmAreIgnored() {
        assertThat(authorities(Map.of("resource_access", Map.of("account", Map.of("roles", List.of("admin")))))).isEmpty();
    }

    private List<String> authorities(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("s")
                .claims(values -> values.putAll(claims))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }
}
