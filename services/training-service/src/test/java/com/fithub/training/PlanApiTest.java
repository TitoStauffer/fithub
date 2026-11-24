package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
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
 * Fichas de treino do personal (DEV-0059) com Postgres real: rascunho, edição e publicação com vínculo ativo, uma
 * ficha ativa por aluno, leitura pelo autor depois do vínculo e as fichas vencendo.
 */
class PlanApiTest extends ServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    ExerciseRepository exercises;

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
        exercise = UUID.randomUUID();
        exercises.save(new Exercise(
                exercise,
                Exercise.Kind.CATALOG,
                null,
                "Supino reto",
                Set.of(MuscleGroup.CHEST),
                Equipment.BARBELL,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()));
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

    private void endLink(UUID professional, UUID clientId) {
        jdbc.sql("""
                        update client_link set status = 'ENDED', ended_at = now()
                        where professional_id = :professional and client_id = :client
                        """)
                .param("professional", professional)
                .param("client", clientId)
                .update();
    }

    private static String bearer(UUID account) {
        return "Bearer personal-" + account;
    }

    private static String body(String name, LocalDate startsOn, Integer weeks, String divisions) {
        return """
                {"name": %s, "startsOn": %s, "validityWeeks": %s, "divisions": %s}
                """.formatted(
                        name == null ? "null" : "\"" + name + "\"",
                        startsOn == null ? "null" : "\"" + startsOn + "\"",
                        weeks == null ? "null" : weeks.toString(),
                        divisions);
    }

    /** Exercício do catálogo para as divisões: publicar exige ao menos um em cada (BR-10, DEV-0060). */
    private UUID exercise;

    private String item() {
        return "{\"exerciseId\": \"" + exercise + "\", \"sets\": 3, \"repsMin\": 10}";
    }

    private String abc() {
        return "[" + division("Peito e tríceps") + ", " + division(" ") + ", " + division("Pernas") + "]";
    }

    private String division(String name) {
        return "{\"name\": " + (name == null ? "null" : "\"" + name + "\"") + ", \"items\": [" + item() + "]}";
    }

    private String create(UUID account, UUID clientId, String name, LocalDate startsOn, int weeks) throws Exception {
        String location = mvc.perform(post("/clients/" + clientId + "/plans")
                        .header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, startsOn, weeks, abc())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring("/plans/".length());
    }

    private void publish(UUID account, String planId) throws Exception {
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer(account)))
                .andExpect(status().isOk());
    }

    @Test
    void personalCriaORascunhoEditaEPublica() throws Exception {
        mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer(personal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" Hipertrofia ABC ", TODAY, 8, abc())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith("/plans/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.name").value("Hipertrofia ABC"))
                .andExpect(jsonPath("$.expiresOn").value(TODAY.plusWeeks(8).toString()))
                .andExpect(jsonPath("$.divisions[*].letter").value(Matchers.contains("A", "B", "C")))
                .andExpect(jsonPath("$.divisions[0].name").value("Peito e tríceps"))
                .andExpect(jsonPath("$.divisions[1].name").isEmpty())
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
        String planId = create(personal, client, "Força AB", TODAY, 4);

        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer(personal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "Força ABCD",
                                TODAY.plusDays(2),
                                6,
                                "[" + division(null) + ", " + division(null) + ", " + division(null) + ", "
                                        + division("Core") + "]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Força ABCD"))
                .andExpect(jsonPath("$.validityWeeks").value(6))
                .andExpect(jsonPath("$.divisions[*].letter").value(Matchers.contains("A", "B", "C", "D")))
                .andExpect(jsonPath("$.divisions[3].name").value("Core"));

        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer(personal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/clients/" + client + "/plans").header("Authorization", bearer(personal)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Força ABCD"));

        // Publicada não é editada como rascunho (a nova versão é a DEV-0061) nem publicada de novo.
        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer(personal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Outra", TODAY, 4, abc())))
                .andExpect(status().isConflict());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer(personal)))
                .andExpect(status().isConflict());
    }

    @Test
    void publicarOutraFichaDeixaAAnteriorSubstituida() throws Exception {
        String first = create(personal, client, "Ficha 1", TODAY, 4);
        String second = create(personal, client, "Ficha 2", TODAY, 4);
        publish(personal, first);

        publish(personal, second);

        mvc.perform(get("/plans/" + first).header("Authorization", bearer(personal)))
                .andExpect(jsonPath("$.status").value("REPLACED"));
        mvc.perform(get("/plans/" + second).header("Authorization", bearer(personal)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/plans/" + first + "/publish").header("Authorization", bearer(personal)))
                .andExpect(status().isConflict());
    }

    @Test
    void semVinculoNaoAlteraMasOAutorContinuaLendo() throws Exception {
        String planId = create(personal, client, "Ficha", TODAY, 4);
        publish(personal, planId);
        String draft = create(personal, client, "Rascunho", TODAY, 4);
        endLink(personal, client);

        mvc.perform(post("/clients/" + client + "/plans")
                        .header("Authorization", bearer(personal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nova", TODAY, 4, abc())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + client + "/plans").header("Authorization", bearer(personal)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/plans/" + draft)
                        .header("Authorization", bearer(personal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Editada", TODAY, 4, abc())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/plans/" + draft + "/publish").header("Authorization", bearer(personal)))
                .andExpect(status().isNotFound());

        // O autor mantém as prescrições que criou (BR-05).
        mvc.perform(get("/plans/" + planId).header("Authorization", bearer(personal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ficha"));
        mvc.perform(get("/plans/" + draft).header("Authorization", bearer(personal)))
                .andExpect(jsonPath("$.name").value("Rascunho"));
    }

    @Test
    void outroPersonalNuncaVeNemAlteraAFicha() throws Exception {
        String planId = create(personal, client, "Ficha", TODAY, 4);
        UUID other = UUID.randomUUID();
        // Mesmo com vínculo próprio com o aluno, a ficha não é dele.
        insertLink(other, UUID.randomUUID(), "ACTIVE");

        mvc.perform(get("/plans/" + planId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ficha não encontrada."));
        mvc.perform(put("/plans/" + planId)
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Tomada", TODAY, 4, abc())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/plans/" + planId + "/publish").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/plans/" + planId).header("Authorization", "Bearer personal-nao-e-uuid"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/plans/" + planId).header("Authorization", "Bearer conta-" + personal))
                .andExpect(status().isForbidden());
        mvc.perform(get("/plans/" + planId)).andExpect(status().isUnauthorized());
    }

    @Test
    void camposForaDasRegrasApontamOCampo() throws Exception {
        String sixtyOne = "a".repeat(61);
        for (String[] invalid : new String[][] {
            {body("a", TODAY, 4, abc()), "name"},
            {body(null, TODAY, 4, abc()), "name"},
            {body("Ficha", null, 4, abc()), "startsOn"},
            {body("Ficha", TODAY, 0, abc()), "validityWeeks"},
            {body("Ficha", TODAY, 53, abc()), "validityWeeks"},
            {body("Ficha", TODAY, null, abc()), "validityWeeks"},
            {body("Ficha", TODAY, 4, "[]"), "divisions"},
            {body("Ficha", TODAY, 4, "null"), "divisions"},
            {body("Ficha", TODAY, 4, "[{},{},{},{},{},{},{},{}]"), "divisions"},
            {body("Ficha", TODAY, 4, "[{\"name\": \"" + sixtyOne + "\"}]"), "divisions[0].name"}
        }) {
            mvc.perform(post("/clients/" + client + "/plans")
                            .header("Authorization", bearer(personal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(invalid[1]));
        }
        mvc.perform(get("/plans/expiring?withinDays=61").header("Authorization", bearer(personal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("withinDays"));
    }

    @Test
    void fichasVencendoTrazemSoAsPublicadasDoPersonalComVinculoAtivo() throws Exception {
        jdbc.sql("insert into client (client_id, name, updated_at) values (:id, 'Ana Aluna', now())")
                .param("id", client)
                .update();
        // Vence em 3 dias.
        String soon = create(personal, client, "Vence logo", TODAY.minusWeeks(1).plusDays(3), 1);
        publish(personal, soon);
        // Já venceu.
        UUID second = UUID.randomUUID();
        insertLink(personal, second, "ACTIVE");
        String expired = create(personal, second, "Vencida", TODAY.minusWeeks(2), 1);
        publish(personal, expired);
        // Vence em 30 dias: fora do período padrão de 7.
        UUID third = UUID.randomUUID();
        insertLink(personal, third, "ACTIVE");
        String later =
                create(personal, third, "Vence depois", TODAY.minusWeeks(1).plusDays(30), 1);
        publish(personal, later);
        // Rascunho vencendo: não conta.
        create(personal, third, "Rascunho", TODAY.minusWeeks(1), 1);
        // Aluno cujo vínculo acabou: não conta.
        UUID gone = UUID.randomUUID();
        insertLink(personal, gone, "ACTIVE");
        String ended = create(personal, gone, "Sem vínculo", TODAY.minusWeeks(2), 1);
        publish(personal, ended);
        endLink(personal, gone);
        // Ficha de outro personal: não conta.
        UUID other = UUID.randomUUID();
        UUID otherClient = UUID.randomUUID();
        insertLink(other, otherClient, "ACTIVE");
        publish(other, create(other, otherClient, "De outro", TODAY.minusWeeks(2), 1));

        mvc.perform(get("/plans/expiring").header("Authorization", bearer(personal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(Matchers.contains("Vencida", "Vence logo")))
                .andExpect(jsonPath("$[1].clientName").value("Ana Aluna"))
                .andExpect(jsonPath("$[1].expiresOn").value(TODAY.plusDays(3).toString()))
                .andExpect(jsonPath("$[0].clientName").isEmpty());
        mvc.perform(get("/plans/expiring?withinDays=30").header("Authorization", bearer(personal)))
                .andExpect(jsonPath("$[*].name").value(Matchers.contains("Vencida", "Vence logo", "Vence depois")));
    }

    /** As CHECKs e o índice único da V8 recusam sem passar pelo domínio. */
    @Test
    void bancoRecusaEstadoValidadeVersaoNomeDivisaoEDuasPublicadas() throws Exception {
        String insertPlan = """
                insert into workout_plan (id, client_id, personal_id, status, created_at, updated_at)
                values (:id, :client, :personal, :status, now(), now())
                """;
        assertThatThrownBy(() -> jdbc.sql(insertPlan)
                        .param("id", UUID.randomUUID())
                        .param("client", client)
                        .param("personal", personal)
                        .param("status", "ARQUIVADA")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_workout_plan_status");

        String planId = create(personal, client, "Ficha", TODAY, 4);
        publish(personal, planId);
        assertThatThrownBy(() -> jdbc.sql(insertPlan)
                        .param("id", UUID.randomUUID())
                        .param("client", client)
                        .param("personal", personal)
                        .param("status", "PUBLISHED")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_workout_plan_published");

        String insertVersion = """
                insert into workout_plan_version (id, plan_id, number, name, starts_on, validity_weeks, created_at)
                values (:id, :plan, :number, :name, current_date, :weeks, now())
                """;
        record Version(int number, String name, int weeks, String constraint) {}
        for (Version row : new Version[] {
            new Version(0, "Ficha", 4, "ck_workout_plan_version_number"),
            new Version(2, " ", 4, "ck_workout_plan_version_name"),
            new Version(2, "Ficha", 53, "ck_workout_plan_version_validity"),
            new Version(1, "Ficha", 4, "uq_workout_plan_version")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insertVersion)
                            .param("id", UUID.randomUUID())
                            .param("plan", UUID.fromString(planId))
                            .param("number", row.number())
                            .param("name", row.name())
                            .param("weeks", row.weeks())
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }

        UUID versionId = jdbc.sql("select id from workout_plan_version where plan_id = :plan")
                .param("plan", UUID.fromString(planId))
                .query(UUID.class)
                .single();
        record Division(int position, String letter, String constraint) {}
        for (Division row : new Division[] {
            new Division(7, "H", "ck_workout_division_position"), new Division(5, "A", "ck_workout_division_letter")
        }) {
            assertThatThrownBy(() -> jdbc.sql("""
                                    insert into workout_division (version_id, position, letter)
                                    values (:version, :position, :letter)
                                    """)
                            .param("version", versionId)
                            .param("position", row.position())
                            .param("letter", row.letter())
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
    }
}
