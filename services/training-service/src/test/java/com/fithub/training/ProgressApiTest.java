package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
import com.fithub.training.domain.progress.ProgressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Evolução do treino (DEV-0091) com Postgres real: aderência, carga e volume, filtros, acesso e a última atividade. */
class ProgressApiTest extends ServiceIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.now(ZONE);

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    ProgressRepository progressRepository;

    private UUID personal;
    private UUID client;
    private UUID squat;
    private UUID legPress;

    @TestConfiguration
    static class Tokens {

        /** {@code personal-<sub>}: personal; {@code nutri-<sub>}: nutricionista; {@code conta-<sub>}: cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix = Map.of(
                    "conta-",
                    List.of("client"),
                    "personal-",
                    List.of("client", "personal"),
                    "nutri-",
                    List.of("client", "nutritionist"));
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

    private void link(UUID student) {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :personal, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", student)
                .param("personal", personal)
                .update();
    }

    @BeforeEach
    void setUp() {
        personal = UUID.randomUUID();
        client = UUID.randomUUID();
        link(client);
        squat = saveExercise("Agachamento livre");
        legPress = saveExercise("Leg press");
    }

    private String personalToken() {
        return "Bearer personal-" + personal;
    }

    /** Ficha ABC do personal, publicada há {@code daysAgo} dias. */
    private UUID abcPlan(int daysAgo) throws Exception {
        String item = "{\"exerciseId\": \"" + squat + "\", \"sets\": 3, \"repsMin\": 10}";
        String body = "{\"name\": \"ABC\", \"startsOn\": \"" + TODAY
                + "\", \"validityWeeks\": 8, \"divisions\": [{\"items\": [" + item + "]}, {\"items\": [" + item
                + "]}, {\"items\": [" + item + "]}]}";
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", personalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        UUID planId = UUID.fromString(location.substring("/plans/".length()));
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", personalToken()))
                .andExpect(status().isOk());
        jdbc.sql(
                        "update workout_plan_version set published_at = published_at - make_interval(days => :days) where plan_id = :plan")
                .param("days", daysAgo)
                .param("plan", planId)
                .update();
        return planId;
    }

    /** Ficha própria do aluno (uma divisão), publicada há {@code daysAgo} dias. */
    private UUID ownPlan(int daysAgo) throws Exception {
        String body = "{\"name\": \"Minha\", \"startsOn\": \"" + TODAY
                + "\", \"validityWeeks\": 4, \"divisions\": [{\"items\": [" + "{\"exerciseId\": \"" + legPress
                + "\", \"sets\": 3, \"repsMin\": 10}]}]}";
        String location = mvc.perform(post("/me/own-plans")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        UUID planId = UUID.fromString(location.substring("/me/own-plans/".length()));
        mvc.perform(post("/me/own-plans/" + planId + "/publish").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());
        jdbc.sql(
                        "update workout_plan_version set published_at = published_at - make_interval(days => :days) where plan_id = :plan")
                .param("days", daysAgo)
                .param("plan", planId)
                .update();
        return planId;
    }

    /** Um treino finalizado há {@code daysAgo} dias (às 12h de Brasília), com as séries dadas (carga, repetições). */
    private UUID finishedSession(UUID planId, int daysAgo, UUID exercise, String... sets) {
        UUID session = UUID.randomUUID();
        Instant finished = TODAY.minusDays(daysAgo).atTime(12, 0).atZone(ZONE).toInstant();
        UUID version = jdbc.sql(
                        "select id from workout_plan_version where plan_id = :plan order by number desc limit 1")
                .param("plan", planId)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into workout_session (id, client_id, plan_id, version_id, division_position, started_at, finished_at)
                        values (:id, :client, :plan, :version, 0, :finished, :finished)
                        """)
                .param("id", session)
                .param("client", client)
                .param("plan", planId)
                .param("version", version)
                .param("finished", java.sql.Timestamp.from(finished))
                .update();
        for (int index = 0; index < sets.length; index++) {
            String[] parts = sets[index].split("x");
            jdbc.sql("""
                            insert into workout_set (id, session_id, item_position, set_number, exercise_id, load_kg, reps,
                                recorded_at, updated_at)
                            values (:id, :session, 0, :number, :exercise, :load, :reps, now(), now())
                            """)
                    .param("id", UUID.randomUUID())
                    .param("session", session)
                    .param("number", index + 1)
                    .param("exercise", exercise)
                    .param("load", new BigDecimal(parts[0]))
                    .param("reps", Integer.parseInt(parts[1]))
                    .update();
        }
        return session;
    }

    private String period() {
        return "?from=" + TODAY.minusDays(13) + "&to=" + TODAY;
    }

    @Test
    void aderenciaECargaDoAlunoNasFichasDoPersonal() throws Exception {
        UUID abc = abcPlan(30);
        finishedSession(abc, 2, squat, "40x10", "50x8", "60x0");
        finishedSession(abc, 4, squat, "40x10");
        finishedSession(abc, 10, squat, "35x12");
        // Fora do período: não conta.
        finishedSession(abc, 20, squat, "30x10");
        // O repositório já devolve só o intervalo pedido.
        Instant from = TODAY.minusDays(13).atStartOfDay(ZONE).toInstant();
        Instant to = TODAY.plusDays(1).atStartOfDay(ZONE).toInstant();
        assertThat(progressRepository.finishedSessions(client, personal, from, to))
                .hasSize(3);

        mvc.perform(get("/clients/" + client + "/progress/training" + period())
                        .header("Authorization", personalToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(TODAY.minusDays(13).toString()))
                .andExpect(jsonPath("$.adherence.done").value(3))
                .andExpect(jsonPath("$.adherence.expected").value(6.0))
                .andExpect(jsonPath("$.adherence.percent").value(50.0))
                .andExpect(jsonPath("$.adherence.weeks.length()").value(2))
                .andExpect(jsonPath("$.adherence.weeks[0].done").value(1))
                .andExpect(jsonPath("$.adherence.weeks[1].done").value(2))
                .andExpect(jsonPath("$.adherence.weeks[1].expected").value(3.0))
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].name").value("Agachamento livre"))
                .andExpect(jsonPath("$.exercises[0].points.length()").value(3))
                .andExpect(jsonPath("$.exercises[0].points[2].date")
                        .value(TODAY.minusDays(2).toString()))
                // A série sem repetição não conta para a maior carga; o volume soma carga × repetições.
                .andExpect(jsonPath("$.exercises[0].points[2].maxLoadKg").value(50.0))
                .andExpect(jsonPath("$.exercises[0].points[2].volumeKg").value(800.0));
    }

    @Test
    void aFichaPropriaContaSoParaOAluno() throws Exception {
        UUID abc = abcPlan(30);
        UUID own = ownPlan(30);
        finishedSession(abc, 2, squat, "40x10");
        finishedSession(own, 3, legPress, "100x10");

        mvc.perform(get("/clients/" + client + "/progress/training" + period())
                        .header("Authorization", personalToken()))
                .andExpect(jsonPath("$.adherence.done").value(1))
                .andExpect(jsonPath("$.adherence.expected").value(6.0))
                .andExpect(jsonPath("$.exercises.length()").value(1));
        // O aluno vê as duas fichas: a ABC (3 por semana) e a própria (1 por semana).
        mvc.perform(get("/me/progress/training" + period()).header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherence.done").value(2))
                .andExpect(jsonPath("$.adherence.expected").value(8.0))
                .andExpect(jsonPath("$.adherence.percent").value(25.0))
                .andExpect(jsonPath("$.exercises.length()").value(2));
    }

    @Test
    void aFichaSubstituidaEsperaSoAteADataEmQueSaiu() throws Exception {
        UUID first = abcPlan(30);
        jdbc.sql("update workout_plan set status = 'REPLACED', updated_at = now() - interval '7 days' where id = :id")
                .param("id", first)
                .update();
        mvc.perform(get("/clients/" + client + "/progress/training" + period())
                        .header("Authorization", personalToken()))
                // Valeu do início do período até 7 dias atrás: 7 dias × 3 / 7 = 3.
                .andExpect(jsonPath("$.adherence.expected").value(3.0));
    }

    @Test
    void semVinculoPeriodoInvalidoEPapeis() throws Exception {
        mvc.perform(get("/clients/" + client + "/progress/training")
                        .header("Authorization", "Bearer personal-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + client + "/progress/training")
                        .header("Authorization", "Bearer nutri-" + personal))
                .andExpect(status().isForbidden());
        mvc.perform(get("/clients/" + client + "/progress/training?from=" + TODAY + "&to=" + TODAY.minusDays(1))
                        .header("Authorization", personalToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        mvc.perform(get("/me/progress/training?from=" + TODAY.minusDays(182) + "&to=" + TODAY)
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        // Sem período, as últimas 4 semanas.
        mvc.perform(get("/clients/" + client + "/progress/training").header("Authorization", personalToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(TODAY.minusDays(27).toString()))
                .andExpect(jsonPath("$.to").value(TODAY.toString()))
                .andExpect(jsonPath("$.adherence.percent").doesNotExist());
    }

    @Test
    void ultimaAtividadeDeCadaAlunoVinculado() throws Exception {
        UUID abc = abcPlan(30);
        finishedSession(abc, 5, squat, "40x10");
        finishedSession(abc, 2, squat, "40x10");
        // O treino mais recente, na ficha própria, não é atividade nas fichas do personal.
        finishedSession(ownPlan(30), 1, legPress, "100x10");
        UUID idle = UUID.randomUUID();
        link(idle);
        // Aluno de outro personal não aparece.
        UUID otherPersonal = UUID.randomUUID();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :personal, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", UUID.randomUUID())
                .param("personal", otherPersonal)
                .update();

        Instant last = TODAY.minusDays(2).atTime(12, 0).atZone(ZONE).toInstant();
        mvc.perform(get("/professionals/me/clients/activity").header("Authorization", personalToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clientId").value(client.toString()))
                .andExpect(jsonPath("$[0].lastActivityAt").value(last.toString()))
                .andExpect(jsonPath("$[1].clientId").value(idle.toString()))
                .andExpect(jsonPath("$[1].lastActivityAt").doesNotExist());
        mvc.perform(get("/professionals/me/clients/activity").header("Authorization", "Bearer nutri-" + personal))
                .andExpect(status().isForbidden());
    }
}
