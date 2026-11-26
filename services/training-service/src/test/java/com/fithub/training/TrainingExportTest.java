package com.fithub.training;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Exportação dos dados do cliente no training (DEV-0166): fichas que o cliente vê (sem o rascunho do personal), a
 * ficha própria, sessões com séries e comentários, recordes e restrições; nada de outro cliente; só para o cliente.
 */
class TrainingExportTest extends ServiceIntegrationTest {

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

    /** Cliente com uma ficha publicada do personal, um rascunho do personal, a ficha própria, sessões e restrição. */
    private UUID seed(String comment) {
        UUID client = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        UUID exercise = UUID.randomUUID();
        jdbc.sql("""
                        insert into exercise (id, kind, owner_id, name, equipment, created_at, updated_at)
                        values (:id, 'OWN', :owner, 'Supino exportado', 'BARBELL', now(), now())
                        """).param("id", exercise).param("owner", personal).update();
        jdbc.sql("insert into exercise_muscle_group (exercise_id, muscle_group) values (:id, 'CHEST')")
                .param("id", exercise)
                .update();
        UUID personalPlan = plan(client, personal, true, exercise);
        plan(client, personal, false, exercise);
        UUID ownPlan = plan(client, null, true, exercise);
        session(client, personalPlan, exercise, comment);
        session(client, ownPlan, exercise, null);
        jdbc.sql("""
                        insert into mobility_restriction (id, client_id, region, description, recorded_by, created_at,
                                                          updated_at)
                        values (:id, :c, 'KNEE', 'dor no joelho', :p, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", personal)
                .update();
        return client;
    }

    /** Ficha com uma versão; publicada ou rascunho; {@code personal} nulo é a ficha própria do aluno. */
    private UUID plan(UUID client, UUID personal, boolean published, UUID exercise) {
        UUID plan = UUID.randomUUID();
        jdbc.sql("""
                        insert into workout_plan (id, client_id, personal_id, status, author, created_at, updated_at)
                        values (:id, :c, :p, :status, :author, now(), now())
                        """)
                .param("id", plan)
                .param("c", client)
                .param("p", personal)
                .param("status", published ? "PUBLISHED" : "DRAFT")
                .param("author", personal == null ? "CLIENT" : "PERSONAL")
                .update();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                        insert into workout_plan_version (id, plan_id, number, name, starts_on, validity_weeks,
                                                          published_at, created_at)
                        values (:id, :plan, 1, :name, date '2026-11-01', 8,
                                case when :published then now() end, now())
                        """)
                .param("id", version)
                .param("plan", plan)
                .param("name", published ? "Ficha entregue" : "Rascunho do personal")
                .param("published", published)
                .update();
        // A ficha tem ao menos um treino com um exercício (invariante do domínio).
        jdbc.sql("insert into workout_division (version_id, position, letter) values (:v, 0, 'A')")
                .param("v", version)
                .update();
        jdbc.sql("""
                        insert into workout_item (version_id, division_position, position, exercise_id, sets, reps_min,
                                                  reps_max)
                        values (:v, 0, 0, :e, 3, 8, 12)
                        """).param("v", version).param("e", exercise).update();
        return plan;
    }

    private void session(UUID client, UUID plan, UUID exercise, String comment) {
        UUID session = UUID.randomUUID();
        jdbc.sql("""
                        insert into workout_session (id, client_id, plan_id, version_id, division_position, started_at,
                                                     finished_at, comment, feeling)
                        select :id, :c, :plan, v.id, 0, now(), now(), :comment, 4
                          from workout_plan_version v where v.plan_id = :plan
                        """)
                .param("id", session)
                .param("c", client)
                .param("plan", plan)
                .param("comment", comment)
                .update();
        jdbc.sql("""
                        insert into workout_set (id, session_id, item_position, set_number, exercise_id, load_kg, reps,
                                                 recorded_at, updated_at)
                        values (:id, :s, 0, 1, :e, 60, 10, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("s", session)
                .param("e", exercise)
                .update();
    }

    @Test
    void exportaAsFichasQueOClienteVeAsSessoesOsRecordesEAsRestricoes() throws Exception {
        UUID client = seed("cansado");
        seed("de outra pessoa");

        mvc.perform(get("/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.plans.length()").value(2))
                .andExpect(jsonPath("$.plans[*].versions[*].name")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("Ficha entregue"))))
                .andExpect(jsonPath("$.plans[?(@.personalId)]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[*].comment").value(org.hamcrest.Matchers.contains("cansado")))
                .andExpect(jsonPath("$.sessions[0].sets[0].exerciseName").value("Supino exportado"))
                .andExpect(jsonPath("$.sessions[0].sets[0].reps").value(10))
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.restrictions.length()").value(1))
                .andExpect(jsonPath("$.restrictions[0].description").value("dor no joelho"));
    }

    @Test
    void contaSemNadaExportaListasVazias() throws Exception {
        mvc.perform(get("/me/export").header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans.length()").value(0))
                .andExpect(jsonPath("$.sessions.length()").value(0))
                .andExpect(jsonPath("$.records.length()").value(0))
                .andExpect(jsonPath("$.restrictions.length()").value(0));
    }

    @Test
    void semTokenOuSemOPapelDeClienteNaoExporta() throws Exception {
        mvc.perform(get("/me/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/export").header("Authorization", "Bearer sempapel-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
