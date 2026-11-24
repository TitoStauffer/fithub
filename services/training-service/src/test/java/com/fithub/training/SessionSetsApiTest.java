package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.fithub.training.domain.session.SetRepository;
import com.fithub.training.domain.session.WorkoutSet;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Registro do treino por série (DEV-0066) com Postgres real: confirmar o prescrito, idempotência pelo id, a versão da
 * ficha da sessão, o prazo de correção do dia e o pré-preenchimento com o último treino.
 */
class SessionSetsApiTest extends ServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    SetRepository sets;

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

    private String clientToken() {
        return "Bearer conta-" + client;
    }

    private static String plan(Integer baseVersion, UUID exercise, int sets, int reps, String load) {
        return """
                {"baseVersion": %s, "name": "Pernas", "startsOn": "%s", "validityWeeks": 8,
                 "divisions": [{"items": [{"exerciseId": "%s", "sets": %d, "repsMin": %d, "loadKg": %s}]}]}
                """.formatted(baseVersion == null ? "null" : baseVersion, TODAY, exercise, sets, reps, load);
    }

    /** Ficha de uma divisão, publicada: toda sessão é a divisão A. */
    private String publishedPlan() throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(null, squat, 3, 10, "40")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String planId = location.substring("/plans/".length());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        return planId;
    }

    /** Ficha de uma divisão com dois exercícios (agachamento e leg press), publicada, para o aluno informado. */
    private String twoItemPlan(UUID student) throws Exception {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :personal, 'ACTIVE', now())
                        on conflict do nothing
                        """)
                .param("id", UUID.randomUUID())
                .param("client", student)
                .param("personal", personal)
                .update();
        String body = """
                {"name": "Pernas", "startsOn": "%s", "validityWeeks": 8,
                 "divisions": [{"items": [{"exerciseId": "%s", "sets": 3, "repsMin": 10},
                                          {"exerciseId": "%s", "sets": 3, "repsMin": 10}]}]}
                """.formatted(TODAY, squat, legPress);
        String location = mvc.perform(post("/clients/" + student + "/plans")
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String planId = location.substring("/plans/".length());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        return planId;
    }

    private UUID startSessionAs(UUID student, String planId) throws Exception {
        UUID session = UUID.randomUUID();
        mvc.perform(put("/me/sessions/" + session)
                        .header("Authorization", "Bearer conta-" + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + planId + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private UUID startSession(String planId) throws Exception {
        UUID session = UUID.randomUUID();
        mvc.perform(put("/me/sessions/" + session)
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + planId + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private static String set(int item, int number, String load, int reps) {
        return "{\"itemPosition\": %d, \"setNumber\": %d, \"loadKg\": %s, \"reps\": %d}"
                .formatted(item, number, load, reps);
    }

    private void record(UUID session, UUID setId, String body) throws Exception {
        mvc.perform(put("/me/sessions/" + session + "/sets/" + setId)
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private int setsIn(UUID session) {
        return jdbc.sql("select count(*) from workout_set where session_id = :session")
                .param("session", session)
                .query(Integer.class)
                .single();
    }

    @Test
    void confirmarOPrescritoRegistraEORepetidoNaoDuplica() throws Exception {
        UUID session = startSession(publishedPlan());
        UUID setId = UUID.randomUUID();

        mvc.perform(put("/me/sessions/" + session + "/sets/" + setId)
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(set(0, 1, "40", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setNumber").value(1))
                .andExpect(jsonPath("$.loadKg").value(40.0))
                .andExpect(jsonPath("$.reps").value(10))
                .andExpect(jsonPath("$.exerciseId").value(squat.toString()));
        record(session, setId, set(0, 1, "40", 10));
        assertThat(setsIn(session)).isEqualTo(1);

        // O mesmo id corrige.
        record(session, setId, set(0, 1, "42.25", 8));
        mvc.perform(get("/me/sessions/" + session).header("Authorization", clientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctable").value(true))
                .andExpect(jsonPath("$.items[0].exercise.name").value("Agachamento livre"))
                .andExpect(jsonPath("$.items[0].sets").value(3))
                .andExpect(jsonPath("$.items[0].repsMin").value(10))
                .andExpect(jsonPath("$.items[0].loadKg").value(40.0))
                .andExpect(jsonPath("$.items[0].recorded.length()").value(1))
                .andExpect(jsonPath("$.items[0].recorded[0].loadKg").value(42.25))
                .andExpect(jsonPath("$.items[0].recorded[0].reps").value(8));
        assertThat(setsIn(session)).isEqualTo(1);

        // Outro id no mesmo lugar (item e número) é repetição: 409.
        mvc.perform(put("/me/sessions/" + session + "/sets/" + UUID.randomUUID())
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(set(0, 1, "40", 10)))
                .andExpect(status().isConflict());
    }

    @Test
    void seriesFicamNaVersaoDaSessaoEOProximoTreinoUsaANova() throws Exception {
        String planId = publishedPlan();
        UUID first = startSession(planId);
        record(first, UUID.randomUUID(), set(0, 1, "40", 10));

        // O personal edita a ficha no meio da semana: versão 2 com leg press.
        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan(1, legPress, 4, 8, "120")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/me/sessions/" + first).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.items[0].exercise.name").value("Agachamento livre"))
                .andExpect(jsonPath("$.items[0].recorded.length()").value(1));
        record(first, UUID.randomUUID(), set(0, 2, "40", 10));
        mvc.perform(post("/me/sessions/" + first + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());

        UUID second = startSession(planId);
        mvc.perform(get("/me/sessions/" + second).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.items[0].exercise.name").value("Leg press"))
                .andExpect(jsonPath("$.items[0].sets").value(4));
    }

    @Test
    void corrigirEApagarSoNoMesmoDia() throws Exception {
        UUID session = startSession(publishedPlan());
        UUID kept = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        record(session, kept, set(0, 1, "40", 10));
        record(session, removed, set(0, 2, "40", 10));

        mvc.perform(delete("/me/sessions/" + session + "/sets/" + removed).header("Authorization", clientToken()))
                .andExpect(status().isNoContent());
        assertThat(setsIn(session)).isEqualTo(1);

        // O treino passa a ser de ontem: o prazo acabou.
        jdbc.sql("update workout_session set started_at = started_at - interval '1 day' where id = :id")
                .param("id", session)
                .update();
        mvc.perform(put("/me/sessions/" + session + "/sets/" + kept)
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(set(0, 1, "50", 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("prazo")));
        mvc.perform(delete("/me/sessions/" + session + "/sets/" + kept).header("Authorization", clientToken()))
                .andExpect(status().isConflict());
        mvc.perform(get("/me/sessions/" + session).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.correctable").value(false))
                .andExpect(jsonPath("$.items[0].recorded[0].loadKg").value(40.0));
    }

    @Test
    void preenchimentoTrazAsSeriesDoUltimoTreinoComOExercicio() throws Exception {
        String planId = publishedPlan();
        UUID first = startSession(planId);
        record(first, UUID.randomUUID(), set(0, 1, "40", 10));
        record(first, UUID.randomUUID(), set(0, 2, "42.5", 8));
        mvc.perform(post("/me/sessions/" + first + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());

        UUID second = startSession(planId);

        mvc.perform(get("/me/sessions/" + second).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.items[0].recorded").isEmpty())
                .andExpect(jsonPath("$.items[0].last.length()").value(2))
                .andExpect(jsonPath("$.items[0].last[0].loadKg").value(40.0))
                .andExpect(jsonPath("$.items[0].last[1].loadKg").value(42.5))
                .andExpect(jsonPath("$.items[0].last[1].reps").value(8));
    }

    @Test
    void serieForaDasRegrasOuForaDoTreinoApontaOCampo() throws Exception {
        UUID session = startSession(publishedPlan());

        for (String[] invalid : new String[][] {
            {set(5, 1, "40", 10), "itemPosition"},
            {set(0, 0, "40", 10), "setNumber"},
            {set(0, 1, "40.1", 10), "loadKg"},
            {set(0, 1, "40", 301), "reps"},
            {"{\"itemPosition\": 0, \"setNumber\": 1, \"reps\": 10}", "loadKg"}
        }) {
            mvc.perform(put("/me/sessions/" + session + "/sets/" + UUID.randomUUID())
                            .header("Authorization", clientToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(invalid[1]));
        }
    }

    @Test
    void sessaoDeOutroAlunoRespondeComoInexistente() throws Exception {
        String planId = publishedPlan();
        UUID session = startSession(planId);
        UUID setId = UUID.randomUUID();
        record(session, setId, set(0, 1, "40", 10));
        String stranger = "Bearer conta-" + UUID.randomUUID();

        mvc.perform(get("/me/sessions/" + session).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mvc.perform(put("/me/sessions/" + session + "/sets/" + UUID.randomUUID())
                        .header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(set(0, 2, "40", 10)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/me/sessions/" + session + "/sets/" + setId).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        assertThat(setsIn(session)).isEqualTo(1);
    }

    /** Code Review do PR #249: o mesmo id não muda a série de item nem de número. */
    @Test
    void corrigirPeloMesmoIdNaoMudaASerieDeExercicioNemDeNumero() throws Exception {
        UUID session = startSession(twoItemPlan(client));
        UUID setId = UUID.randomUUID();
        record(session, setId, set(0, 1, "40", 10));

        for (String moved : new String[] {set(1, 1, "120", 8), set(0, 2, "40", 10)}) {
            mvc.perform(put("/me/sessions/" + session + "/sets/" + setId)
                            .header("Authorization", clientToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(moved))
                    .andExpect(status().isConflict());
        }
        mvc.perform(get("/me/sessions/" + session).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.items[0].recorded.length()").value(1))
                .andExpect(jsonPath("$.items[0].recorded[0].exerciseId").value(squat.toString()))
                .andExpect(jsonPath("$.items[0].recorded[0].setNumber").value(1))
                .andExpect(jsonPath("$.items[0].recorded[0].loadKg").value(40.0))
                .andExpect(jsonPath("$.items[1].recorded").isEmpty());
    }

    /** Code Review do PR #249: o id de uma série de outro treino (do próprio aluno ou de outro) não a altera. */
    @Test
    void serieDeOutroTreinoNaoECorrigidaNemApagadaPeloId() throws Exception {
        String planId = twoItemPlan(client);
        UUID mine = startSession(planId);
        UUID setId = UUID.randomUUID();
        record(mine, setId, set(0, 1, "40", 10));
        mvc.perform(post("/me/sessions/" + mine + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());
        UUID myNext = startSession(planId);
        UUID other = UUID.randomUUID();
        UUID theirs = startSessionAs(other, twoItemPlan(other));

        record Attempt(UUID session, String token) {}
        for (Attempt attempt :
                new Attempt[] {new Attempt(myNext, clientToken()), new Attempt(theirs, "Bearer conta-" + other)}) {
            mvc.perform(put("/me/sessions/" + attempt.session() + "/sets/" + setId)
                            .header("Authorization", attempt.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(set(0, 1, "200", 1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Esse id já é de uma série de outro treino."));
            mvc.perform(delete("/me/sessions/" + attempt.session() + "/sets/" + setId)
                            .header("Authorization", attempt.token()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/me/sessions/" + mine).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.items[0].recorded.length()").value(1))
                .andExpect(jsonPath("$.items[0].recorded[0].loadKg").value(40.0))
                .andExpect(jsonPath("$.items[0].recorded[0].reps").value(10));
    }

    /** O upsert não toca série de outra sessão, mesmo se a leitura da aplicação for driblada (concorrência). */
    @Test
    void gravacaoNoBancoNaoSobrescreveSerieDeOutraSessao() throws Exception {
        String planId = twoItemPlan(client);
        UUID first = startSession(planId);
        UUID setId = UUID.randomUUID();
        record(first, setId, set(0, 1, "40", 10));
        mvc.perform(post("/me/sessions/" + first + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());
        UUID second = startSession(planId);

        boolean saved = sets.save(
                new WorkoutSet(setId, second, 0, 1, squat, new BigDecimal("200"), 1, Instant.now(), Instant.now()));

        assertThat(saved).isFalse();
        assertThat(sets.findById(setId).orElseThrow().sessionId()).isEqualTo(first);
        assertThat(sets.findById(setId).orElseThrow().loadKg()).isEqualByComparingTo("40");
    }

    /** Ficha de uma divisão com agachamento e leg press como alternativa, publicada. */
    private String planWithAlternative() throws Exception {
        String body = """
                {"name": "Pernas", "startsOn": "%s", "validityWeeks": 8,
                 "divisions": [{"items": [{"exerciseId": "%s", "sets": 3, "repsMin": 10,
                                           "alternatives": ["%s"]}]}]}
                """.formatted(TODAY, squat, legPress);
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", "Bearer personal-" + personal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String planId = location.substring("/plans/".length());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        return planId;
    }

    private static String setWith(int item, int number, String load, int reps, UUID exercise) {
        return "{\"itemPosition\": %d, \"setNumber\": %d, \"loadKg\": %s, \"reps\": %d, \"exerciseId\": \"%s\"}"
                .formatted(item, number, load, reps, exercise);
    }

    /** DEV-0067: a série registra a alternativa prevista, e o pré-preenchimento usa o exercício realmente feito. */
    @Test
    void serieComAlternativaPrevistaEPreenchimentoDoExercicioFeito() throws Exception {
        String planId = planWithAlternative();
        UUID first = startSession(planId);
        UUID other = saveExercise("Cadeira extensora");

        mvc.perform(put("/me/sessions/" + first + "/sets/" + UUID.randomUUID())
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setWith(0, 1, "120", 10, legPress)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(legPress.toString()));
        mvc.perform(put("/me/sessions/" + first + "/sets/" + UUID.randomUUID())
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setWith(0, 2, "120", 10, other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("exerciseId"));
        mvc.perform(post("/me/sessions/" + first + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());

        UUID second = startSession(planId);
        mvc.perform(get("/me/sessions/" + second).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.items[0].last").isEmpty())
                .andExpect(jsonPath("$.items[0].alternativesLast[0].exerciseId").value(legPress.toString()))
                .andExpect(
                        jsonPath("$.items[0].alternativesLast[0].sets.length()").value(1))
                .andExpect(jsonPath("$.items[0].alternativesLast[0].sets[0].loadKg")
                        .value(120.0));
    }

    /** DEV-0067: comentário e sensação do treino, no prazo da correção. */
    @Test
    void comentarioESensacaoDoTreino() throws Exception {
        UUID session = startSession(publishedPlan());

        mvc.perform(put("/me/sessions/" + session + "/feedback")
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"  Pesado hoje.  \", \"feeling\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Pesado hoje."))
                .andExpect(jsonPath("$.feeling").value(4));
        for (String[] invalid : new String[][] {
            {"{\"feeling\": 6}", "feeling"},
            {"{\"feeling\": 0}", "feeling"},
            {"{\"comment\": \"" + "a".repeat(501) + "\"}", "comment"}
        }) {
            mvc.perform(put("/me/sessions/" + session + "/feedback")
                            .header("Authorization", clientToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(invalid[1]));
        }
        mvc.perform(post("/me/sessions/" + session + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());
        mvc.perform(get("/me/sessions/" + session).header("Authorization", clientToken()))
                .andExpect(jsonPath("$.comment").value("Pesado hoje."))
                .andExpect(jsonPath("$.feeling").value(4));

        jdbc.sql("update workout_session set started_at = started_at - interval '1 day' where id = :id")
                .param("id", session)
                .update();
        mvc.perform(put("/me/sessions/" + session + "/feedback")
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeling\": 2}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/me/sessions/" + session + "/feedback")
                        .header("Authorization", "Bearer conta-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeling\": 2}"))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> jdbc.sql("update workout_session set feeling = 6 where id = :id")
                        .param("id", session)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_workout_session_feeling");
    }

    /** As CHECKs e a unicidade da V11 recusam sem passar pelo domínio. */
    @Test
    void bancoRecusaCargaRepeticoesSerieItemELugarRepetido() throws Exception {
        UUID session = startSession(publishedPlan());
        String insert = """
                insert into workout_set (id, session_id, item_position, set_number, exercise_id, load_kg, reps,
                    recorded_at, updated_at)
                values (:id, :session, :item, :number, :exercise, :load, :reps, now(), now())
                """;
        record Row(int item, int number, String load, int reps, String constraint) {}
        for (Row row : new Row[] {
            new Row(0, 1, "40.10", 10, "ck_workout_set_load"),
            new Row(0, 1, "40", 301, "ck_workout_set_reps"),
            new Row(0, 21, "40", 10, "ck_workout_set_number"),
            new Row(30, 1, "40", 10, "ck_workout_set_item")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("session", session)
                            .param("item", row.item())
                            .param("number", row.number())
                            .param("exercise", squat)
                            .param("load", new BigDecimal(row.load()))
                            .param("reps", row.reps())
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
        record(session, UUID.randomUUID(), set(0, 1, "40", 10));
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("session", session)
                        .param("item", 0)
                        .param("number", 1)
                        .param("exercise", squat)
                        .param("load", new BigDecimal("40"))
                        .param("reps", 10)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_workout_set_slot");
    }
}
