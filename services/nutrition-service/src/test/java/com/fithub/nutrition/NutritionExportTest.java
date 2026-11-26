package com.fithub.nutrition;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exportação dos dados do cliente no nutrition (DEV-0167): planos recebidos (sem o rascunho da nutricionista), registros
 * com troca, desvio e a URL da foto do prato, restrições alimentares; nada de outro cliente; só para o cliente.
 */
class NutritionExportTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @TestConfiguration
    static class Tokens {

        /** {@code conta-<uuid>} traz o papel de cliente; {@code sempapel-<uuid>}, nenhum. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix = Map.of("conta-", List.of("client"), "sempapel-", List.of());
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

    /** Cliente com um plano publicado, um rascunho da nutricionista, um registro com foto e uma restrição. */
    private UUID seed(String deviation) {
        UUID client = UUID.randomUUID();
        UUID nutritionist = UUID.randomUUID();
        UUID food = UUID.randomUUID();
        jdbc.sql("""
                        insert into food (id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g,
                                          vegan, created_at, updated_at)
                        values (:id, 'OWN', :owner, 'Aveia exportada', 'G', 390, 14, 66, 7, true, now(), now())
                        """).param("id", food).param("owner", nutritionist).update();
        UUID published = plan(client, nutritionist, food, true);
        plan(client, nutritionist, food, false);
        jdbc.sql("""
                        insert into meal_log (id, client_id, plan_id, version_id, version_number, meal_position,
                                              logged_on, status, created_at, updated_at, deviation, photo_key,
                                              photo_uploaded_at)
                        select :id, :c, :plan, v.id, 1, 0, date '2026-11-20', 'PARTIAL', now(), now(), :deviation,
                               :key, now()
                          from meal_plan_version v where v.plan_id = :plan
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("plan", published)
                .param("deviation", deviation)
                .param("key", "meal-logs/%s/%s/photo-%s".formatted(client, UUID.randomUUID(), UUID.randomUUID()))
                .update();
        jdbc.sql("""
                        insert into food_restriction (id, client_id, kind, description, terms, recorded_by, created_at,
                                                      updated_at)
                        values (:id, :c, 'ALLERGY', 'amendoim', array['amendoim'], :n, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("n", nutritionist)
                .update();
        return client;
    }

    /** Plano com uma versão, uma refeição e um alimento; publicado ou rascunho. */
    private UUID plan(UUID client, UUID nutritionist, UUID food, boolean published) {
        UUID plan = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                        insert into meal_plan (id, client_id, nutritionist_id, status, created_at, updated_at)
                        values (:id, :c, :n, :status, now(), now())
                        """)
                .param("id", plan)
                .param("c", client)
                .param("n", nutritionist)
                .param("status", published ? "PUBLISHED" : "DRAFT")
                .update();
        jdbc.sql("""
                        insert into meal_plan_version (id, plan_id, number, name, starts_on, published_at, created_at)
                        values (:id, :plan, 1, :name, date '2026-11-01', case when :published then now() end, now())
                        """)
                .param("id", version)
                .param("plan", plan)
                .param("name", published ? "Plano entregue" : "Rascunho da nutri")
                .param("published", published)
                .update();
        jdbc.sql("insert into meal (version_id, position, name) values (:v, 0, 'Café da manhã')")
                .param("v", version)
                .update();
        jdbc.sql("""
                        insert into meal_item (version_id, meal_position, position, food_id, amount)
                        values (:v, 0, 0, :f, 40)
                        """).param("v", version).param("f", food).update();
        return plan;
    }

    @Test
    void exportaOsPlanosRecebidosOsRegistrosComAFotoEAsRestricoes() throws Exception {
        UUID client = seed("comi pizza");
        seed("de outra pessoa");

        mvc.perform(get("/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.planVersions", hasSize(1)))
                .andExpect(jsonPath("$.planVersions[*].name", everyItem(Matchers.equalTo("Plano entregue"))))
                .andExpect(jsonPath("$.logs", hasSize(1)))
                .andExpect(jsonPath("$.logs[0].deviation").value("comi pizza"))
                .andExpect(jsonPath("$.logs[0].photoUrl").isNotEmpty())
                .andExpect(jsonPath("$.restrictions", hasSize(1)))
                .andExpect(jsonPath("$.restrictions[0].description").value("amendoim"));
    }

    @Test
    void contaSemNadaExportaListasVazias() throws Exception {
        mvc.perform(get("/me/export").header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planVersions", hasSize(0)))
                .andExpect(jsonPath("$.logs", hasSize(0)))
                .andExpect(jsonPath("$.restrictions", hasSize(0)));
    }

    @Test
    void semTokenOuSemOPapelDeClienteNaoExporta() throws Exception {
        mvc.perform(get("/me/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/export").header("Authorization", "Bearer sempapel-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
