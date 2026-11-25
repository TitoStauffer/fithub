package com.fithub.account.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fithub.account.domain.login.LoginAccounts;
import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalRoles;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Atribui o papel de profissional (DEV-0029, ADR-008) e apaga o login de quem excluiu a conta (DEV-0161, ADR-012)
 * pela API de administração do Keycloak, com o client confidencial {@code fithub-account-service} em client
 * credentials.
 *
 * <p>O Keycloak deixa esse client atribuir qualquer papel do realm. A trava está aqui: o único papel que
 * este adaptador atribui é o do tipo de profissional pedido, e o id da conta precisa ser um UUID (o
 * {@code sub} do Keycloak), para não montar caminho arbitrário na API. A exclusão só chega aqui pelo
 * {@code ContaExcluida} do client-service, e nunca para conta de profissional.
 */
@Component
class KeycloakProfessionalRoles implements ProfessionalRoles, LoginAccounts {

    /** Os únicos papéis que o account-service pode atribuir (ADR-007). */
    static final Map<Professional.Type, String> ROLE_BY_TYPE =
            Map.of(Professional.Type.PERSONAL, "personal", Professional.Type.NUTRITIONIST, "nutritionist");

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient http;
    private final Settings settings;

    // O Spring Boot 4 só registra o RestClient.Builder com o módulo spring-boot-restclient; para não
    // trazer dependência nova, o cliente é montado aqui, com timeout de conexão e de leitura.
    @Autowired
    KeycloakProfessionalRoles(Settings settings) {
        this(
                RestClient.builder()
                        .requestFactory(timeouts())
                        .baseUrl(settings.baseUrl())
                        .build(),
                settings);
    }

    /** Para os testes, com um {@link RestClient} já montado. */
    KeycloakProfessionalRoles(RestClient http, Settings settings) {
        this.http = http;
        this.settings = settings;
    }

    private static JdkClientHttpRequestFactory timeouts() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    /**
     * Configuração do acesso à API de administração ({@code fithub.keycloak-admin.*}). O secret vem de
     * variável de ambiente; o padrão do {@code application.yml} só vale para o compose local.
     */
    @ConfigurationProperties("fithub.keycloak-admin")
    record Settings(String baseUrl, String realm, String clientId, String clientSecret) {}

    @Override
    public void assign(String accountId, Professional.Type type) {
        String role = ROLE_BY_TYPE.get(type);
        if (role == null) {
            throw new IllegalArgumentException("Tipo de profissional sem papel: " + type);
        }
        UUID userId = UUID.fromString(accountId);
        String token = accessToken();

        RoleRepresentation representation = http.get()
                .uri("/admin/realms/{realm}/roles/{role}", settings.realm(), role)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(RoleRepresentation.class);
        if (representation == null || !role.equals(representation.name()) || representation.id() == null) {
            throw new IllegalStateException("O Keycloak devolveu um papel diferente do pedido.");
        }

        // Atribuir um papel que a conta já tem não muda nada no Keycloak: a chamada pode ser repetida.
        http.post()
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", settings.realm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(new RoleRepresentation(representation.id(), role)))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void delete(UUID accountId) {
        String token = accessToken();
        try {
            http.delete()
                    .uri("/admin/realms/{realm}/users/{userId}", settings.realm(), accountId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            // Já apagada (reentrega, ou a pessoa nunca chegou a existir no Keycloak): nada a fazer.
        }
    }

    private String accessToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());
        TokenResponse response = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", settings.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("O Keycloak não devolveu token para o account-service.");
        }
        return response.accessToken();
    }

    record RoleRepresentation(String id, String name) {}

    record TokenResponse(@JsonProperty("access_token") String accessToken) {}
}
