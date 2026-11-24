package com.fithub.training;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Alertas de restrição de mobilidade na ficha (DEV-0062) com Postgres real: aluno com restrição de joelho, um
 * agachamento na ficha gera alerta e a ficha é salva e publicada mesmo assim (BR-11).
 */
class PlanAlertsApiTest extends ServiceIntegrationTest {

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
    private UUID bench;
    private UUID calfRaise;

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

    private UUID saveExercise(String name, Set<MuscleGroup> groups) {
        Exercise exercise = new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                null,
                name,
                groups,
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
        squat = saveExercise("Agachamento livre", Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.GLUTES));
        bench = saveExercise("Supino reto", Set.of(MuscleGroup.CHEST, MuscleGroup.TRICEPS));
        calfRaise = saveExercise("Panturrilha em pé", Set.of(MuscleGroup.CALVES));
    }

    private String bearer() {
        return "Bearer personal-" + personal;
    }

    private String restrict(String region, String description) throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/restrictions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\": \"" + region + "\", \"description\": \"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static String plan(String items) {
        return """
                {"name": "Ficha", "startsOn": "%s", "validityWeeks": 8,
                 "divisions": [{"name": "A", "items": [%s]}]}
                """.formatted(START, items);
    }

    private static String item(UUID exercise, UUID... alternatives) {
        StringBuilder list = new StringBuilder();
        for (UUID alternative : alternatives) {
            list.append(list.isEmpty() ? "" : ", ")
                    .append('"')
                    .append(alternative)
                    .append('"');
        }
        return "{\"exerciseId\": \"" + exercise + "\", \"sets\": 3, \"repsMin\": 10, \"alternatives\": [" + list + "]}";
    }

    private String createPlan(String items) throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(items)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring("/plans/".length());
    }

    @Test
    void agachamentoComRestricaoDeJoelhoAlertaESalvaEPublicaMesmoAssim() throws Exception {
        String knee = restrict("KNEE", "Condromalácia patelar.");

        mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(item(bench) + ", " + item(squat))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].divisionPosition").value(0))
                .andExpect(jsonPath("$.alerts[0].divisionLetter").value("A"))
                .andExpect(jsonPath("$.alerts[0].itemPosition").value(1))
                .andExpect(jsonPath("$.alerts[0].exerciseId").value(squat.toString()))
                .andExpect(jsonPath("$.alerts[0].alternative").value(false))
                .andExpect(jsonPath("$.alerts[0].region").value("KNEE"))
                .andExpect(jsonPath("$.alerts[0].restrictionId").value(knee))
                .andExpect(jsonPath("$.alerts[0].description").value("Condromalácia patelar."));

        String planId = createPlan(item(squat));
        // Alerta, não bloqueio: a ficha é publicada com o exercício.
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.alerts[0].region").value("KNEE"));
    }

    @Test
    void exercicioSemRelacaoNaoAlertaEAlternativaComRegiaoAlerta() throws Exception {
        restrict("ANKLE_FOOT", "Entorse recente no tornozelo.");

        String planId = createPlan(item(bench, calfRaise));

        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].exerciseId").value(calfRaise.toString()))
                .andExpect(jsonPath("$.alerts[0].alternative").value(true))
                .andExpect(jsonPath("$.alerts[0].region").value("ANKLE_FOOT"));
    }

    @Test
    void removerARestricaoFazOAlertaSumir() throws Exception {
        String knee = restrict("KNEE", "Dor no joelho.");
        String planId = createPlan(item(squat));
        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.alerts.length()").value(1));

        mvc.perform(delete("/clients/" + client + "/restrictions/" + knee).header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.alerts").isEmpty());
    }

    @Test
    void semVinculoOAutorLeAFichaSemAsRestricoesDoAluno() throws Exception {
        restrict("KNEE", "Dor no joelho.");
        String planId = createPlan(item(squat));
        jdbc.sql("update client_link set status = 'ENDED', ended_at = now() where professional_id = :personal")
                .param("personal", personal)
                .update();

        mvc.perform(get("/plans/" + planId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts").isEmpty());
    }
}
