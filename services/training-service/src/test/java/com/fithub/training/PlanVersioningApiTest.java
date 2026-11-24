package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Versões da ficha (DEV-0061) com Postgres real: editar a publicada cria a versão seguinte sem mexer na anterior,
 * versão base desatualizada dá 409, rascunho edita no lugar, substituída não muda e o aluno lê as versões publicadas.
 */
class PlanVersioningApiTest extends ServiceIntegrationTest {

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

    private UUID saveExercise(String name) {
        Exercise exercise = new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                null,
                name,
                Set.of(MuscleGroup.QUADRICEPS),
                Equipment.BARBELL,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
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
        squat = saveExercise("Agachamento livre");
        legPress = saveExercise("Leg press");
    }

    private String bearer() {
        return "Bearer personal-" + personal;
    }

    private static String plan(Integer baseVersion, String name, UUID exercise, int sets) {
        return """
                {"baseVersion": %s, "name": "%s", "startsOn": "%s", "validityWeeks": 8,
                 "divisions": [{"name": "Pernas", "items": [{"exerciseId": "%s", "sets": %d, "repsMin": 10}]}]}
                """.formatted(baseVersion == null ? "null" : baseVersion, name, START, exercise, sets);
    }

    private String publishedPlan() throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(null, "Pernas v1", squat, 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String planId = location.substring("/plans/".length());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer()))
                .andExpect(status().isOk());
        return planId;
    }

    private String edit(String planId, Integer baseVersion, String name, UUID exercise, int sets) throws Exception {
        return mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(baseVersion, name, exercise, sets)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void editarAPublicadaCriaAVersaoSeguinteSemMexerNaAnterior() throws Exception {
        String planId = publishedPlan();
        String before = mvc.perform(get("/plans/" + planId + "/versions/1").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(1, "Pernas v2", legPress, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.name").value("Pernas v2"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.divisions[0].items[0].exerciseId").value(legPress.toString()));

        // A versão 1 continua idêntica.
        String after = mvc.perform(get("/plans/" + planId + "/versions/1").header("Authorization", bearer()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(after).isEqualTo(before);
        mvc.perform(get("/plans/" + planId + "/versions/1").header("Authorization", bearer()))
                .andExpect(jsonPath("$.name").value("Pernas v1"))
                .andExpect(jsonPath("$.divisions[0].items[0].exerciseId").value(squat.toString()))
                .andExpect(jsonPath("$.divisions[0].items[0].sets").value(3));
        mvc.perform(get("/plans/" + planId + "/versions").header("Authorization", bearer()))
                .andExpect(jsonPath("$[*].number").value(Matchers.contains(1, 2)))
                .andExpect(jsonPath("$[*].name").value(Matchers.contains("Pernas v1", "Pernas v2")));
        mvc.perform(get("/plans/" + planId + "/versions/3").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void versaoBaseDesatualizadaRespondeConflitoSemGravar() throws Exception {
        String planId = publishedPlan();
        edit(planId, 1, "Pernas v2", legPress, 4);

        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(1, "Atrasada", squat, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A ficha mudou desde que você a abriu. Recarregue antes de editar."));
        mvc.perform(get("/plans/" + planId + "/versions").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.name").value("Pernas v2"));
    }

    @Test
    void rascunhoEditaNoLugarSemGerarVersao() throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(null, "Rascunho", squat, 3)))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String planId = location.substring("/plans/".length());

        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(null, "Rascunho editado", legPress, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/plans/" + planId + "/versions").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Rascunho editado"))
                .andExpect(jsonPath("$[0].publishedAt").doesNotExist());
    }

    @Test
    void substituidaNaoMudaESemVinculoEditarRespondeComoInexistente() throws Exception {
        String first = publishedPlan();
        String second = publishedPlan();

        mvc.perform(get("/plans/" + first).header("Authorization", bearer()))
                .andExpect(jsonPath("$.status").value("REPLACED"));
        mvc.perform(put("/plans/" + first)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(1, "Ressuscitada", squat, 3)))
                .andExpect(status().isConflict());

        jdbc.sql("update client_link set status = 'ENDED', ended_at = now() where professional_id = :personal")
                .param("personal", personal)
                .update();
        mvc.perform(put("/plans/" + second)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(1, "Sem vínculo", squat, 3)))
                .andExpect(status().isNotFound());
        // O autor continua lendo as versões do que criou (BR-05).
        mvc.perform(get("/plans/" + second + "/versions/1").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void alunoLeAsVersoesPublicadasDaPropriaFicha() throws Exception {
        String planId = publishedPlan();
        edit(planId, 1, "Pernas v2", legPress, 4);
        String draftLocation = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(null, "Rascunho", squat, 3)))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String draft = draftLocation.substring("/plans/".length());

        mvc.perform(get("/me/plans/" + planId + "/versions").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number").value(Matchers.contains(1, 2)));
        mvc.perform(get("/me/plans/" + planId + "/versions/1").header("Authorization", "Bearer conta-" + client))
                .andExpect(jsonPath("$.name").value("Pernas v1"));
        // Rascunho e ficha de outro aluno não aparecem, nem na lista.
        mvc.perform(get("/me/plans/" + draft + "/versions").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isNotFound());
        mvc.perform(get("/me/plans/" + draft + "/versions/1").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isNotFound());
        mvc.perform(get("/me/plans/" + planId + "/versions/1")
                        .header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
