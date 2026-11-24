package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Prescrição por exercício na ficha (DEV-0060) com Postgres real: itens em ordem com alternativas, exercícios
 * disponíveis para o autor, publicação só com exercício em cada divisão (BR-10) e as CHECKs da V9.
 */
class PrescriptionApiTest extends ServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 11, 24);

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private UUID personal;
    private UUID client;
    private UUID squat;
    private UUID legPress;

    @TestConfiguration
    static class Tokens {

        /** {@code personal-<sub>} traz o papel de personal. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix = Map.of("personal-", List.of("client", "personal"));
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

    private UUID saveExercise(String name, UUID owner) {
        Exercise exercise = new Exercise(
                UUID.randomUUID(),
                owner == null ? Exercise.Kind.CATALOG : Exercise.Kind.OWN,
                owner,
                name,
                Set.of(MuscleGroup.QUADRICEPS),
                Equipment.BARBELL,
                null,
                null,
                null,
                NOW,
                NOW);
        exercises.save(exercise);
        return exercise.id();
    }

    @BeforeEach
    void setUp() {
        personal = UUID.randomUUID();
        client = UUID.randomUUID();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :personal, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", client)
                .param("personal", personal)
                .update();
        squat = saveExercise("Agachamento livre", null);
        legPress = saveExercise("Leg press", null);
    }

    private String bearer() {
        return "Bearer personal-" + personal;
    }

    private static String plan(String divisions) {
        return """
                {"name": "Pernas", "startsOn": "%s", "validityWeeks": 8, "divisions": %s}
                """.formatted(START, divisions);
    }

    /** Item com 3x10; {@code extra} acrescenta campos (e troca as séries, se trouxer {@code sets}). */
    private static String item(UUID exercise, String extra) {
        String sets = extra.contains("\"sets\"") ? "" : ", \"sets\": 3";
        return "{\"exerciseId\": \"" + exercise + "\"" + sets + ", \"repsMin\": 10" + extra + "}";
    }

    private String createDraft(String divisions) throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(divisions)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring("/plans/".length());
    }

    @Test
    void rascunhoGuardaOsItensNaOrdemComPrescricaoEAlternativas() throws Exception {
        UUID mine = saveExercise("Agachamento no banco", personal);
        String divisions = "[{\"name\": \"Pernas\", \"items\": ["
                + item(
                        squat,
                        ", \"repsMax\": 12, \"loadKg\": 60.5, \"restSeconds\": 90, \"note\": \" Desça até 90°. \","
                                + " \"technique\": \"DROP_SET\", \"alternatives\": [\"" + legPress + "\", \"" + mine
                                + "\"]")
                + ", " + item(mine, "") + "]}]";

        String planId = createDraft(divisions);

        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.divisions[0].items.length()").value(2))
                .andExpect(jsonPath("$.divisions[0].items[0].exerciseId").value(squat.toString()))
                .andExpect(jsonPath("$.divisions[0].items[0].sets").value(3))
                .andExpect(jsonPath("$.divisions[0].items[0].repsMin").value(10))
                .andExpect(jsonPath("$.divisions[0].items[0].repsMax").value(12))
                .andExpect(jsonPath("$.divisions[0].items[0].loadKg").value(60.5))
                .andExpect(jsonPath("$.divisions[0].items[0].restSeconds").value(90))
                .andExpect(jsonPath("$.divisions[0].items[0].note").value("Desça até 90°."))
                .andExpect(jsonPath("$.divisions[0].items[0].technique").value("DROP_SET"))
                .andExpect(jsonPath("$.divisions[0].items[0].alternatives")
                        .value(Matchers.contains(legPress.toString(), mine.toString())))
                .andExpect(jsonPath("$.divisions[0].items[1].exerciseId").value(mine.toString()))
                // Repetições fixas: mínimo e máximo iguais.
                .andExpect(jsonPath("$.divisions[0].items[1].repsMax").value(10))
                .andExpect(jsonPath("$.divisions[0].items[1].alternatives").isEmpty());

        // Editar o rascunho troca o conteúdo inteiro.
        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan("[{\"items\": [" + item(legPress, "") + "]}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.divisions[0].items.length()").value(1))
                .andExpect(jsonPath("$.divisions[0].items[0].exerciseId").value(legPress.toString()));
    }

    @Test
    void exercicioIndisponivelNaoEntraNaFicha() throws Exception {
        UUID theirs = saveExercise("Agachamento dele", UUID.randomUUID());
        UUID archived = saveExercise("Agachamento antigo", personal);
        exercises.save(exercises.findById(archived).orElseThrow().archive(NOW));

        for (String[] invalid : new String[][] {
            {item(theirs, ""), "divisions[0].items[0].exerciseId"},
            {item(UUID.randomUUID(), ""), "divisions[0].items[0].exerciseId"},
            {item(archived, ""), "divisions[0].items[0].exerciseId"},
            {
                item(squat, ", \"alternatives\": [\"" + legPress + "\", \"" + theirs + "\"]"),
                "divisions[0].items[0].alternatives[1]"
            }
        }) {
            mvc.perform(post("/clients/" + client + "/plans")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(plan("[{\"items\": [" + invalid[0] + "]}]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(invalid[1]));
        }
    }

    @Test
    void exercicioArquivadoDepoisContinuaNoRascunho() throws Exception {
        UUID mine = saveExercise("Agachamento no banco", personal);
        String planId = createDraft("[{\"items\": [" + item(mine, "") + "]}]");
        exercises.save(exercises.findById(mine).orElseThrow().archive(NOW));

        // Salvar de novo com o mesmo exercício vale: ele já estava na ficha.
        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan("[{\"items\": [" + item(mine, ", \"sets\": 4") + "]}]")))
                .andExpect(status().isOk());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void publicarExigeExercicioEmCadaDivisao() throws Exception {
        String planId = createDraft("[{\"items\": [" + item(squat, "") + "]}, {\"name\": \"Vazia\"}]");

        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("divisions[1].items"));
        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void prescricaoForaDasRegrasApontaADivisaoEOItem() throws Exception {
        for (String[] invalid : new String[][] {
            {item(squat, ", \"sets\": 0"), "sets"},
            {item(squat, ", \"repsMax\": 8"), "repsMax"},
            {item(squat, ", \"loadKg\": 42.3"), "loadKg"},
            {item(squat, ", \"restSeconds\": 601"), "restSeconds"},
            {item(squat, ", \"technique\": \"SUPERSET\""), "technique"},
            {item(squat, ", \"alternatives\": [\"" + squat + "\"]"), "alternatives[0]"}
        }) {
            mvc.perform(post("/clients/" + client + "/plans")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(plan("[{\"items\": []}, {\"items\": [" + item(legPress, "") + ", " + invalid[0]
                                    + "]}]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("divisions[1].items[1]." + invalid[1]));
        }
    }

    /** As CHECKs da V9 recusam sem passar pelo domínio. */
    @Test
    void bancoRecusaSeriesRepeticoesCargaDescansoTecnicaEAlternativa() throws Exception {
        String planId = createDraft("[{\"items\": [" + item(squat, "") + "]}]");
        UUID version = jdbc.sql("select id from workout_plan_version where plan_id = :plan")
                .param("plan", UUID.fromString(planId))
                .query(UUID.class)
                .single();
        String insert = """
                insert into workout_item (version_id, division_position, position, exercise_id, sets, reps_min,
                    reps_max, load_kg, rest_seconds, technique)
                values (:version, 0, :position, :exercise, :sets, :repsMin, :repsMax, :load, :rest, :technique)
                """;
        record Row(
                int position,
                int sets,
                int repsMin,
                int repsMax,
                String load,
                Integer rest,
                String technique,
                String constraint) {}
        for (Row row : new Row[] {
            new Row(30, 3, 10, 10, null, null, null, "ck_workout_item_position"),
            new Row(1, 21, 10, 10, null, null, null, "ck_workout_item_sets"),
            new Row(1, 3, 12, 8, null, null, null, "ck_workout_item_reps"),
            new Row(1, 3, 10, 10, "42.3", null, null, "ck_workout_item_load"),
            new Row(1, 3, 10, 10, null, 601, null, "ck_workout_item_rest"),
            new Row(1, 3, 10, 10, null, null, "SUPERSET", "ck_workout_item_technique")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("version", version)
                            .param("position", row.position())
                            .param("exercise", squat)
                            .param("sets", row.sets())
                            .param("repsMin", row.repsMin())
                            .param("repsMax", row.repsMax())
                            .param("load", row.load() == null ? null : new java.math.BigDecimal(row.load()))
                            .param("rest", row.rest())
                            .param("technique", row.technique())
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }

        String alternative = """
                insert into workout_item_alternative (version_id, division_position, item_position, position,
                    exercise_id)
                values (:version, 0, 0, :position, :exercise)
                """;
        assertThatThrownBy(() -> jdbc.sql(alternative)
                        .param("version", version)
                        .param("position", 3)
                        .param("exercise", legPress)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_workout_item_alternative_position");
    }
}
