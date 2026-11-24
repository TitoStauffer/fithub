package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O treino do aluno (DEV-0065) com Postgres real: fichas ativas com o próximo treino e os exercícios embutidos, a
 * sessão idempotente, uma aberta por ficha, e a sequência A→B→C→A.
 */
class ClientWorkoutApiTest extends ServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private UUID personal;
    private UUID client;
    private UUID squat;
    private UUID mine;

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
        Exercise catalog = new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                null,
                "Agachamento livre",
                Set.of(MuscleGroup.QUADRICEPS),
                Equipment.BARBELL,
                null,
                "https://wger.de/media/agachamento.png",
                new Attribution(
                        "wger", UUID.randomUUID().toString(), "CC-BY-SA 4", "wger.de", "CC-BY-SA 3", "Everkinetic"),
                Instant.now(),
                Instant.now());
        exercises.save(catalog);
        squat = catalog.id();
        Exercise own = Exercise.newOwn(
                personal,
                "Agachamento no banco",
                Set.of(MuscleGroup.QUADRICEPS),
                Equipment.BODYWEIGHT,
                null,
                "https://videos.example/banco",
                Instant.now());
        exercises.save(own);
        mine = own.id();
    }

    private String personalToken() {
        return "Bearer personal-" + personal;
    }

    private String clientToken() {
        return "Bearer conta-" + client;
    }

    private String item(UUID exercise, String extra) {
        return "{\"exerciseId\": \"" + exercise + "\", \"sets\": 3, \"repsMin\": 10" + extra + "}";
    }

    /** Ficha ABC do personal, publicada, começando em {@code startsOn} com validade de {@code weeks} semanas. */
    private String publishedPlan(LocalDate startsOn, int weeks) throws Exception {
        String divisions =
                "[{\"name\": \"Pernas\", \"items\": [" + item(squat, ", \"alternatives\": [\"" + mine + "\"]")
                        + "]}, {\"items\": [" + item(mine, "") + "]}, {\"items\": [" + item(squat, "") + "]}]";
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", personalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ABC\", \"startsOn\": \"" + startsOn + "\", \"validityWeeks\": " + weeks
                                + ", \"divisions\": " + divisions + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String planId = location.substring("/plans/".length());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", personalToken()))
                .andExpect(status().isOk());
        return planId;
    }

    private void start(UUID sessionId, String planId) throws Exception {
        mvc.perform(put("/me/sessions/" + sessionId)
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + planId + "\"}"))
                .andExpect(status().isOk());
    }

    private void finish(UUID sessionId) throws Exception {
        mvc.perform(post("/me/sessions/" + sessionId + "/finish").header("Authorization", clientToken()))
                .andExpect(status().isOk());
    }

    @Test
    void sequenciaAbcAvancaAoFinalizarEVoltaAoInicio() throws Exception {
        String planId = publishedPlan(TODAY, 8);

        for (String expected : List.of("A", "B", "C")) {
            mvc.perform(get("/me/plans/active").header("Authorization", clientToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].planId").value(planId))
                    .andExpect(jsonPath("$[0].nextDivisionLetter").value(expected))
                    .andExpect(jsonPath("$[0].openSessionId").doesNotExist());
            UUID session = UUID.randomUUID();
            start(session, planId);
            mvc.perform(get("/me/plans/active").header("Authorization", clientToken()))
                    .andExpect(jsonPath("$[0].openSessionId").value(session.toString()));
            finish(session);
        }
        // Depois de C, volta para A.
        mvc.perform(get("/me/plans/active").header("Authorization", clientToken()))
                .andExpect(jsonPath("$[0].nextDivisionLetter").value("A"))
                .andExpect(jsonPath("$[0].nextDivision").value(0));
    }

    @Test
    void iniciarComOMesmoIdNaoDuplicaEOutraAbertaDaConflito() throws Exception {
        String planId = publishedPlan(TODAY, 8);
        UUID session = UUID.randomUUID();

        mvc.perform(put("/me/sessions/" + session)
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + planId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.divisionPosition").value(0))
                .andExpect(jsonPath("$.finishedAt").doesNotExist());
        start(session, planId);
        mvc.perform(put("/me/sessions/" + UUID.randomUUID())
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + planId + "\"}"))
                .andExpect(status().isConflict());

        finish(session);
        finish(session);
        Integer count = jdbc.sql("select count(*) from workout_session where plan_id = :plan")
                .param("plan", UUID.fromString(planId))
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sessaoEFichaDeOutroAlunoRespondemComoInexistentes() throws Exception {
        String planId = publishedPlan(TODAY, 8);
        UUID session = UUID.randomUUID();
        start(session, planId);
        String stranger = "Bearer conta-" + UUID.randomUUID();

        mvc.perform(post("/me/sessions/" + session + "/finish").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mvc.perform(put("/me/sessions/" + UUID.randomUUID())
                        .header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + planId + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/me/plans/active").header("Authorization", stranger))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(put("/me/sessions/" + UUID.randomUUID())
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("planId"));
    }

    @Test
    void rascunhoNaoApareceNemAceitaSessao() throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", personalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Rascunho\", \"startsOn\": \"" + TODAY + "\", \"validityWeeks\": 4,"
                                + " \"divisions\": [{\"items\": [" + item(squat, "") + "]}]}"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String draft = location.substring("/plans/".length());

        mvc.perform(get("/me/plans/active").header("Authorization", clientToken()))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(put("/me/sessions/" + UUID.randomUUID())
                        .header("Authorization", clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\": \"" + draft + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void itensTrazemNomeMidiaEAtribuicaoInclusiveDoExercicioProprioDoPersonal() throws Exception {
        publishedPlan(TODAY, 8);

        mvc.perform(get("/me/plans/active").header("Authorization", clientToken()))
                .andExpect(jsonPath("$[0].divisions.length()").value(3))
                .andExpect(jsonPath("$[0].divisions[0].letter").value("A"))
                .andExpect(jsonPath("$[0].divisions[0].name").value("Pernas"))
                .andExpect(jsonPath("$[0].divisions[0].items[0].exercise.id").value(squat.toString()))
                .andExpect(jsonPath("$[0].divisions[0].items[0].exercise.name").value("Agachamento livre"))
                .andExpect(jsonPath("$[0].divisions[0].items[0].exercise.mediaUrl")
                        .value("https://wger.de/media/agachamento.png"))
                .andExpect(jsonPath("$[0].divisions[0].items[0].exercise.attribution.author")
                        .value("wger.de"))
                .andExpect(jsonPath("$[0].divisions[0].items[0].exercise.attribution.mediaAuthor")
                        .value("Everkinetic"))
                .andExpect(jsonPath("$[0].divisions[0].items[0].sets").value(3))
                .andExpect(jsonPath("$[0].divisions[0].items[0].alternatives[0].name")
                        .value("Agachamento no banco"))
                .andExpect(jsonPath("$[0].divisions[1].items[0].exercise.name").value("Agachamento no banco"))
                .andExpect(
                        jsonPath("$[0].divisions[1].items[0].exercise.mediaUrl").value("https://videos.example/banco"))
                .andExpect(jsonPath("$[0].divisions[1].items[0].exercise.attribution")
                        .doesNotExist());
    }

    @Test
    void fichaVencidaApareceMarcadaEAceitaSessao() throws Exception {
        String planId = publishedPlan(TODAY.minusWeeks(3), 1);

        mvc.perform(get("/me/plans/active").header("Authorization", clientToken()))
                .andExpect(jsonPath("$[0].expired").value(true))
                .andExpect(jsonPath("$[0].expiresOn").value(TODAY.minusWeeks(2).toString()));
        start(UUID.randomUUID(), planId);
    }

    /** As CHECKs e o índice da V10 recusam sem passar pelo domínio. */
    @Test
    void bancoRecusaDivisaoFimAntesDoInicioEDuasAbertas() throws Exception {
        String planId = publishedPlan(TODAY, 8);
        UUID version = jdbc.sql("select id from workout_plan_version where plan_id = :plan")
                .param("plan", UUID.fromString(planId))
                .query(UUID.class)
                .single();
        String insert = """
                insert into workout_session (id, client_id, plan_id, version_id, division_position, started_at,
                    finished_at)
                values (:id, :client, :plan, :version, :division, now(), :finished)
                """;
        record Row(int division, java.sql.Timestamp finished, String constraint) {}
        for (Row row : new Row[] {
            new Row(7, null, "ck_workout_session_division"),
            new Row(0, java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), "ck_workout_session_finished")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("client", client)
                            .param("plan", UUID.fromString(planId))
                            .param("version", version)
                            .param("division", row.division())
                            .param("finished", row.finished())
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
        start(UUID.randomUUID(), planId);
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("client", client)
                        .param("plan", UUID.fromString(planId))
                        .param("version", version)
                        .param("division", 1)
                        .param("finished", null)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_workout_session_open");
    }
}
