package com.fithub.account.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fithub.account.domain.professional.Professional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** O que o account-service pede à API de administração do Keycloak (DEV-0029, ADR-008). */
class KeycloakProfessionalRolesTest {

    private static final String BASE = "http://keycloak.test";
    private static final String USER = "0fc78876-f97e-4479-bea9-3759453619c1";
    private static final KeycloakProfessionalRoles.Settings SETTINGS =
            new KeycloakProfessionalRoles.Settings(BASE, "fithub", "fithub-account-service", "segredo-de-teste");

    private MockRestServiceServer keycloak;
    private KeycloakProfessionalRoles roles;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        keycloak = MockRestServiceServer.bindTo(builder).build();
        roles = new KeycloakProfessionalRoles(builder.build(), SETTINGS);
    }

    private void expectToken() {
        keycloak.expect(requestTo(BASE + "/realms/fithub/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content()
                        .formDataContains(java.util.Map.of(
                                "grant_type", "client_credentials",
                                "client_id", "fithub-account-service",
                                "client_secret", "segredo-de-teste")))
                .andRespond(
                        withSuccess("{\"access_token\": \"token-da-service-account\"}", MediaType.APPLICATION_JSON));
    }

    private void expectRole(String role, String body) {
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/roles/" + role))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-da-service-account"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void personalRecebeSoOPapelPersonal() {
        expectToken();
        expectRole("personal", "{\"id\": \"id-personal\", \"name\": \"personal\"}");
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/users/" + USER + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-da-service-account"))
                .andExpect(content().json("[{\"id\": \"id-personal\", \"name\": \"personal\"}]", true))
                .andRespond(withNoContent());

        roles.assign(USER, Professional.Type.PERSONAL);

        keycloak.verify();
    }

    @Test
    void nutricionistaRecebeSoOPapelNutritionist() {
        expectToken();
        expectRole("nutritionist", "{\"id\": \"id-nutri\", \"name\": \"nutritionist\"}");
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/users/" + USER + "/role-mappings/realm"))
                .andExpect(content().json("[{\"id\": \"id-nutri\", \"name\": \"nutritionist\"}]", true))
                .andRespond(withNoContent());

        roles.assign(USER, Professional.Type.NUTRITIONIST);

        keycloak.verify();
    }

    @Test
    void papelDevolvidoComOutroNomeNaoEAtribuido() {
        expectToken();
        // Resposta adulterada ou configuração errada: o Keycloak devolve o admin no lugar do personal.
        expectRole("personal", "{\"id\": \"id-admin\", \"name\": \"admin\"}");

        assertThatThrownBy(() -> roles.assign(USER, Professional.Type.PERSONAL))
                .isInstanceOf(IllegalStateException.class);
        keycloak.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../roles/admin", "abc", "0fc78876-f97e-4479-bea9-3759453619c1/role-mappings", ""})
    void contaQueNaoEUmUuidNaoChegaAoKeycloak(String accountId) {
        assertThatThrownBy(() -> roles.assign(accountId, Professional.Type.PERSONAL))
                .isInstanceOf(IllegalArgumentException.class);
        keycloak.verify();
    }

    @Test
    void tokenRecusadoFalhaSemAtribuir() {
        keycloak.expect(requestTo(BASE + "/realms/fithub/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> roles.assign(USER, Professional.Type.PERSONAL))
                .isInstanceOf(RestClientException.class);
        keycloak.verify();
    }

    @Test
    void erroDoKeycloakNaAtribuicaoViraExcecao() {
        expectToken();
        expectRole("personal", "{\"id\": \"id-personal\", \"name\": \"personal\"}");
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/users/" + USER + "/role-mappings/realm"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> roles.assign(USER, Professional.Type.PERSONAL))
                .isInstanceOf(RestClientException.class);
        keycloak.verify();
    }

    @Test
    void excluirApagaOUsuarioDaContaNoRealm() {
        expectToken();
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/users/" + USER))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer token-da-service-account"))
                .andRespond(withNoContent());

        roles.delete(java.util.UUID.fromString(USER));

        keycloak.verify();
    }

    @Test
    void usuarioQueJaNaoExisteContaComoApagado() {
        expectToken();
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/users/" + USER))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        roles.delete(java.util.UUID.fromString(USER));

        keycloak.verify();
    }

    @Test
    void falhaDoKeycloakNaExclusaoSobeParaOConsumoTentarDeNovo() {
        expectToken();
        keycloak.expect(requestTo(BASE + "/admin/realms/fithub/users/" + USER))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        assertThatThrownBy(() -> roles.delete(java.util.UUID.fromString(USER))).isInstanceOf(RestClientException.class);
        keycloak.verify();
    }
}
