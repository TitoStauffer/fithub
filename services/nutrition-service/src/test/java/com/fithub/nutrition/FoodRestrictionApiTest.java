package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;

/** Restrições alimentares com Postgres real (DEV-0080): vínculo, visibilidade, limites e as CHECKs. */
class FoodRestrictionApiTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private UUID nutritionist;
    private UUID client;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>}: nutricionista; {@code personal-<sub>}: personal; {@code conta-<sub>}: cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix = Map.of(
                    "nutri-",
                    List.of("client", "nutritionist"),
                    "personal-",
                    List.of("client", "personal"),
                    "conta-",
                    List.of("client"));
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
        nutritionist = UUID.randomUUID();
        client = UUID.randomUUID();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :professional, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", client)
                .param("professional", nutritionist)
                .update();
    }

    private String nutri() {
        return "Bearer nutri-" + nutritionist;
    }

    private static String body(String kind, String description, String terms) {
        return "{\"kind\": \"" + kind + "\", \"description\": \"" + description + "\", \"terms\": " + terms + "}";
    }

    private ResultActions create(String token, UUID patient, String json) throws Exception {
        return mvc.perform(post("/clients/" + patient + "/food-restrictions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void nutricionistaVinculadaCriaEditaEApagaEOPacienteLe() throws Exception {
        String location = create(
                        nutri(), client, body("ALLERGY", "Alergia à proteína do leite", "[\"leite\", \"queijo\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("ALLERGY"))
                .andExpect(jsonPath("$.terms[1]").value("queijo"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        mvc.perform(put(location)
                        .header("Authorization", nutri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("INTOLERANCE", "Intolerância à lactose", "[\"leite\", \"lactose\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("INTOLERANCE"))
                .andExpect(jsonPath("$.terms[1]").value("lactose"));
        mvc.perform(get("/clients/" + client + "/food-restrictions").header("Authorization", nutri()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/me/food-restrictions").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Intolerância à lactose"));
        mvc.perform(delete(location).header("Authorization", nutri())).andExpect(status().isNoContent());
        mvc.perform(get("/me/food-restrictions").header("Authorization", "Bearer conta-" + client))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void semVinculoOuDeOutroPacienteResponde404EOPersonalNaoTemRota() throws Exception {
        String location = create(nutri(), client, body("PREFERENCE", "Não gosta de peixe", "[\"peixe\"]"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String stranger = "Bearer nutri-" + UUID.randomUUID();

        mvc.perform(get("/clients/" + client + "/food-restrictions").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        create(stranger, client, body("ALLERGY", "Amendoim", "[\"amendoim\"]")).andExpect(status().isNotFound());
        mvc.perform(put(location)
                        .header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ALLERGY", "Editada sem vínculo", "[\"peixe\"]")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(location).header("Authorization", stranger)).andExpect(status().isNotFound());
        // A restrição de outro paciente pelo caminho de um paciente vinculado: não existe.
        UUID other = UUID.randomUUID();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :professional, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", other)
                .param("professional", nutritionist)
                .update();
        String restrictionId = location.substring(location.lastIndexOf('/') + 1);
        mvc.perform(delete("/clients/" + other + "/food-restrictions/" + restrictionId)
                        .header("Authorization", nutri()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + client + "/food-restrictions")
                        .header("Authorization", "Bearer personal-" + nutritionist))
                .andExpect(status().isForbidden());
    }

    @Test
    void camposForaDasRegrasEMaisDeTrinta() throws Exception {
        create(nutri(), client, body("ALLERGY", "a", "[\"leite\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        create(nutri(), client, body("ALLERGY", "Lactose", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("terms"));
        create(nutri(), client, body("ALLERGY", "Lactose", "[\"leite\", \"x\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("terms[1]"));
        for (int index = 0; index < 30; index++) {
            create(nutri(), client, body("PREFERENCE", "Preferência " + index, "[\"termo\"]"))
                    .andExpect(status().isCreated());
        }
        create(nutri(), client, body("PREFERENCE", "A trigésima primeira", "[\"termo\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("restrictions"));
    }

    @Test
    void bancoRecusaRestricaoForaDasRegras() {
        String insert = """
                insert into food_restriction (id, client_id, kind, description, terms, recorded_by, created_at, updated_at)
                values (:id, :client, :kind, :description, cast(:terms as text[]), :by, now(), now())
                """;
        record Row(String kind, String description, String[] terms, String constraint) {}
        for (Row row : new Row[] {
            new Row("XX", "Lactose", new String[] {"leite"}, "ck_food_restriction_kind"),
            new Row("ALLERGY", " a ", new String[] {"leite"}, "ck_food_restriction_description"),
            new Row("ALLERGY", "Lactose", new String[] {}, "ck_food_restriction_terms"),
            new Row(
                    "ALLERGY",
                    "Lactose",
                    new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"},
                    "ck_food_restriction_terms")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("client", client)
                            .param("kind", row.kind())
                            .param("description", row.description())
                            .param("terms", row.terms())
                            .param("by", nutritionist)
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
    }
}
