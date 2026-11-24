package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Restrições de mobilidade do aluno (DEV-0058) com Postgres real: o personal vinculado registra, edita e remove; sem
 * vínculo, 404; o aluno lê as próprias.
 */
class RestrictionApiTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private UUID personal;
    private UUID client;

    @TestConfiguration
    static class Tokens {

        /** {@code personal-<sub>} traz o papel de personal; {@code conta-<sub>}, só o de cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("conta-", List.of("client"), "personal-", List.of("client", "personal"));
            return token -> rolesByPrefix.entrySet().stream()
                    .filter(entry -> token.startsWith(entry.getKey()))
                    .findFirst()
                    .map(entry -> Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject(token.substring(entry.getKey().length()))
                            .claim("realm_access", Map.of("roles", entry.getValue()))
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(300))
                            .build())
                    .orElseThrow(() -> new BadJwtException("token inválido"));
        }
    }

    @BeforeEach
    void link() {
        personal = UUID.randomUUID();
        client = UUID.randomUUID();
        insertLink(personal, client, "ACTIVE");
    }

    private void insertLink(UUID professional, UUID clientId, String status) {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at, ended_at)
                        values (:id, :client, :professional, :status, now(),
                            case when :status = 'ENDED' then now() end)
                        """)
                .param("id", UUID.randomUUID())
                .param("client", clientId)
                .param("professional", professional)
                .param("status", status)
                .update();
    }

    private String path() {
        return "/clients/" + client + "/restrictions";
    }

    private static String body(String region, String description) {
        return "{\"region\": %s, \"description\": %s}"
                .formatted(
                        region == null ? "null" : "\"" + region + "\"",
                        description == null ? "null" : "\"" + description + "\"");
    }

    private String create(String region, String description) throws Exception {
        String location = mvc.perform(post(path())
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(region, description)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void personalVinculadoRegistraEditaERemove() throws Exception {
        mvc.perform(post(path())
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("KNEE", "  Condromalácia patelar, evitar flexão profunda.  ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith(path() + "/")))
                .andExpect(jsonPath("$.region").value("KNEE"))
                .andExpect(jsonPath("$.description").value("Condromalácia patelar, evitar flexão profunda."));
        String lumbar = create("LUMBAR", "Hérnia de disco L4-L5.");

        mvc.perform(get(path()).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].region").value(Matchers.contains("KNEE", "LUMBAR")));

        mvc.perform(put(path() + "/" + lumbar)
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("THORACIC", "Escoliose leve.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("THORACIC"))
                .andExpect(jsonPath("$.description").value("Escoliose leve."));

        mvc.perform(delete(path() + "/" + lumbar).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isNoContent());
        mvc.perform(get(path()).header("Authorization", "Bearer personal-" + personal))
                .andExpect(jsonPath("$[*].region").value(Matchers.contains("KNEE")));
    }

    @Test
    void semVinculoAtivoTudoRespondeComoInexistente() throws Exception {
        String knee = create("KNEE", "Dor no joelho.");
        UUID other = UUID.randomUUID();
        UUID ended = UUID.randomUUID();
        insertLink(ended, client, "ENDED");

        for (UUID stranger : List.of(other, ended)) {
            String token = "Bearer personal-" + stranger;
            mvc.perform(get(path()).header("Authorization", token)).andExpect(status().isNotFound());
            mvc.perform(post(path())
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("HIP", "Quadril.")))
                    .andExpect(status().isNotFound());
            mvc.perform(put(path() + "/" + knee)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("HIP", "Quadril.")))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(path() + "/" + knee).header("Authorization", token))
                    .andExpect(status().isNotFound());
        }
        // A restrição continua lá para o personal vinculado.
        mvc.perform(get(path()).header("Authorization", "Bearer personal-" + personal))
                .andExpect(jsonPath("$[0].description").value("Dor no joelho."));
    }

    @Test
    void restricaoDeOutroAlunoRespondeComoInexistente() throws Exception {
        UUID otherClient = UUID.randomUUID();
        insertLink(personal, otherClient, "ACTIVE");
        String knee = create("KNEE", "Dor no joelho.");

        // Mesmo personal, outro aluno no caminho: a restrição não é dele.
        mvc.perform(put("/clients/" + otherClient + "/restrictions/" + knee)
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HIP", "Quadril.")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Restrição não encontrada."));
        mvc.perform(delete("/clients/" + otherClient + "/restrictions/" + knee)
                        .header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isNotFound());
        mvc.perform(get(path()).header("Authorization", "Bearer personal-" + personal))
                .andExpect(jsonPath("$[0].region").value("KNEE"));
    }

    @Test
    void regiaoForaDaListaOuDescricaoForaDoTamanhoApontaOCampo() throws Exception {
        for (String[] invalid : new String[][] {
            {body("JOELHO", "Dor."), "region"},
            {body(null, "Dor."), "region"},
            {body("KNEE", "   "), "description"},
            {body("KNEE", null), "description"},
            {body("KNEE", "a".repeat(501)), "description"}
        }) {
            mvc.perform(post(path())
                            .header("Authorization", "Bearer personal-" + personal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(invalid[1]));
        }
    }

    @Test
    void alunoLeSoAsPropriasRestricoes() throws Exception {
        create("KNEE", "Dor no joelho.");
        UUID otherClient = UUID.randomUUID();
        insertLink(personal, otherClient, "ACTIVE");
        mvc.perform(post("/clients/" + otherClient + "/restrictions")
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("LUMBAR", "Lombar.")))
                .andExpect(status().isCreated());

        mvc.perform(get("/me/restrictions").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].region").value(Matchers.contains("KNEE")));
        mvc.perform(get("/me/restrictions").header("Authorization", "Bearer conta-nao-e-uuid"))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void soOPapelDePersonalGerenciaRestricoes() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer conta-" + personal))
                .andExpect(status().isForbidden());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/restrictions")).andExpect(status().isUnauthorized());
    }

    /** As CHECKs da V7 recusam sem passar pelo domínio. */
    @Test
    void bancoRecusaRegiaoForaDaListaEDescricaoEmBranco() {
        String insert = """
                insert into mobility_restriction (id, client_id, region, description, recorded_by, created_at,
                    updated_at)
                values (:id, :client, :region, :description, :personal, now(), now())
                """;
        record Row(String region, String description, String constraint) {}
        for (Row row : new Row[] {
            new Row("JOELHO", "Dor.", "ck_mobility_restriction_region"),
            new Row("KNEE", "   ", "ck_mobility_restriction_description")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("client", client)
                            .param("region", row.region())
                            .param("description", row.description())
                            .param("personal", personal)
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
    }
}
