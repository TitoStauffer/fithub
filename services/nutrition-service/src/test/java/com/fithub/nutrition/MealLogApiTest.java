package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
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

/** Registro de refeição com Postgres real (DEV-0087): hoje, idempotência, prazo do dia, versão, acesso e as CHECKs. */
class MealLogApiTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    FoodRepository foods;

    private UUID nutritionist;
    private UUID client;
    private Food rice;
    private Food beans;
    private Food pasta;
    private String planId;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>}: nutricionista; {@code conta-<sub>}: cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("nutri-", List.of("client", "nutritionist"), "conta-", List.of("client"));
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
    void setUp() throws Exception {
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
        rice = food("Arroz, tipo 1, cozido", "128", "2.5");
        beans = food("Feijão, carioca, cozido", "76", "4.8");
        pasta = food("Macarrão, trigo, cozido", "102", "3.4");
        planId = publishedPlan();
    }

    private Food food(String name, String kcal, String protein) {
        Food food = new Food(
                UUID.randomUUID(),
                Food.Kind.TACO,
                null,
                name,
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal(kcal), new BigDecimal(protein), new BigDecimal("20"), new BigDecimal("1"), null),
                true,
                new Food.Source("TACO 4ª edição", UUID.randomUUID().toString()),
                Instant.now(),
                Instant.now());
        foods.save(food);
        return food;
    }

    private String nutri() {
        return "Bearer nutri-" + nutritionist;
    }

    private String patient() {
        return "Bearer conta-" + client;
    }

    /** Café: arroz (alternativas macarrão e feijão) e feijão; almoço: feijão. */
    private String planBody(String name) {
        return """
                {"name": "%s", "startsOn": "2026-12-01", "meals": [
                  {"name": "Café", "items": [
                    {"foodId": "%s", "amount": 100, "alternatives": [{"foodId": "%s", "amount": 80}, {"foodId": "%s", "amount": 50}]},
                    {"foodId": "%s", "amount": 50}]},
                  {"name": "Almoço", "items": [{"foodId": "%s", "amount": 100}]}]}
                """.formatted(name, rice.id(), pasta.id(), beans.id(), beans.id(), beans.id());
    }

    private String createPlan(String name) throws Exception {
        String location = mvc.perform(post("/clients/" + client + "/meal-plans")
                        .header("Authorization", nutri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String publishedPlan() throws Exception {
        String id = createPlan("Plano de dezembro");
        mvc.perform(post("/meal-plans/" + id + "/publish").header("Authorization", nutri()))
                .andExpect(status().isOk());
        return id;
    }

    private static String body(String plan, Object meal, String status, String substitutions) {
        return "{\"planId\": " + (plan == null ? "null" : "\"" + plan + "\"") + ", \"mealPosition\": " + meal
                + ", \"status\": " + (status == null ? "null" : "\"" + status + "\"") + ", \"substitutions\": "
                + substitutions + "}";
    }

    private ResultActions log(String token, UUID logId, String json) throws Exception {
        return mvc.perform(put("/me/meal-logs/" + logId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void hojeMostraOPlanoPublicadoComOsMacrosSemAlertasEOsRegistrosDoDia() throws Exception {
        mvc.perform(
                        post("/clients/" + client + "/food-restrictions")
                                .header("Authorization", nutri())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"kind\": \"ALLERGY\", \"description\": \"Alergia a arroz\", \"terms\": [\"arroz\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.id").value(planId))
                .andExpect(
                        jsonPath("$.plan.meals[0].items[0].nutrients.proteinG").value(2.5))
                .andExpect(jsonPath("$.plan.meals[0].items[0].alternatives.length()")
                        .value(2))
                // O alerta é da nutricionista: o paciente não o recebe.
                .andExpect(jsonPath("$.plan.meals[0].items[0].alerts.length()").value(0))
                .andExpect(jsonPath("$.logs.length()").value(0));

        UUID logId = UUID.randomUUID();
        log(patient(), logId, body(planId, 0, "DONE", "[{\"itemPosition\": 0, \"alternative\": 1}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.logs.length()").value(1))
                .andExpect(jsonPath("$.logs[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.logs[0].substitutions[0].alternative").value(1));
    }

    @Test
    void oMesmoIdCorrigeEOutroIdNaMesmaRefeicaoResponde409() throws Exception {
        UUID logId = UUID.randomUUID();
        log(patient(), logId, body(planId, 0, "DONE", "[{\"itemPosition\": 0, \"alternative\": 1}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.substitutions[0].itemPosition").value(0));
        log(patient(), logId, body(planId, 0, "PARTIAL", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.substitutions.length()").value(0));
        log(patient(), UUID.randomUUID(), body(planId, 0, "SKIPPED", "[]")).andExpect(status().isConflict());
        // O mesmo id em outra refeição também é conflito: o registro é daquela refeição.
        log(patient(), logId, body(planId, 1, "DONE", "[]")).andExpect(status().isConflict());
        log(patient(), UUID.randomUUID(), body(planId, 1, "SKIPPED", null)).andExpect(status().isOk());
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.logs.length()").value(2))
                .andExpect(jsonPath("$.logs[0].status").value("PARTIAL"));
    }

    @Test
    void depoisDoDiaNaoCorrigeNemApaga() throws Exception {
        UUID yesterday = UUID.randomUUID();
        log(patient(), yesterday, body(planId, 0, "DONE", "[]")).andExpect(status().isOk());
        jdbc.sql("update meal_log set logged_on = logged_on - 1 where id = :id")
                .param("id", yesterday)
                .update();
        log(patient(), yesterday, body(planId, 0, "SKIPPED", "[]")).andExpect(status().isConflict());
        mvc.perform(delete("/me/meal-logs/" + yesterday).header("Authorization", patient()))
                .andExpect(status().isConflict());

        // A refeição de ontem não ocupa a de hoje, e o registro de hoje se apaga no dia.
        UUID today = UUID.randomUUID();
        log(patient(), today, body(planId, 0, "DONE", "[]")).andExpect(status().isOk());
        mvc.perform(delete("/me/meal-logs/" + today).header("Authorization", patient()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.logs.length()").value(0));
    }

    @Test
    void camposForaDasRegrasApontamOCampo() throws Exception {
        record Case(String json, String field) {}
        for (Case wrong : List.of(
                new Case(body(null, 0, "DONE", "[]"), "planId"),
                new Case(body(planId, 0, null, "[]"), "status"),
                new Case(body(planId, 5, "DONE", "[]"), "mealPosition"),
                new Case(body(planId, null, "DONE", "[]"), "mealPosition"),
                new Case(body(planId, 0, "SKIPPED", "[{\"itemPosition\": 0, \"alternative\": 0}]"), "substitutions"),
                new Case(
                        body(planId, 0, "DONE", "[{\"itemPosition\": 3, \"alternative\": 0}]"),
                        "substitutions[0].itemPosition"),
                new Case(
                        body(
                                planId,
                                0,
                                "DONE",
                                "[{\"itemPosition\": 0, \"alternative\": 0}, {\"itemPosition\": 0, \"alternative\": 1}]"),
                        "substitutions[1].itemPosition"),
                new Case(
                        body(planId, 0, "DONE", "[{\"itemPosition\": 0, \"alternative\": 2}]"),
                        "substitutions[0].alternative"),
                new Case(
                        body(planId, 0, "DONE", "[{\"itemPosition\": 1, \"alternative\": 0}]"),
                        "substitutions[0].alternative"),
                new Case(body(planId, 0, "DONE", "[null]"), "substitutions[0]"))) {
            log(patient(), UUID.randomUUID(), wrong.json())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(wrong.field()));
        }
    }

    @Test
    void soNoPlanoPublicadoDoProprioPaciente() throws Exception {
        String stranger = "Bearer conta-" + UUID.randomUUID();
        mvc.perform(get("/me/meal-plan/today").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        log(stranger, UUID.randomUUID(), body(planId, 0, "DONE", "[]")).andExpect(status().isNotFound());
        log(patient(), UUID.randomUUID(), body(UUID.randomUUID().toString(), 0, "DONE", "[]"))
                .andExpect(status().isNotFound());

        // O id do registro de um paciente não serve para outro: nem corrige, nem apaga.
        UUID logId = UUID.randomUUID();
        log(patient(), logId, body(planId, 0, "DONE", "[]")).andExpect(status().isOk());
        log(stranger, logId, body(planId, 0, "SKIPPED", "[]")).andExpect(status().isConflict());
        // O id é deste registro: com outro plano, também é conflito.
        log(patient(), logId, body(UUID.randomUUID().toString(), 0, "DONE", "[]"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/me/meal-logs/" + logId).header("Authorization", stranger))
                .andExpect(status().isNotFound());

        // O rascunho não recebe registro; o plano substituído também não.
        String draft = createPlan("Plano novo");
        log(patient(), UUID.randomUUID(), body(draft, 0, "DONE", "[]")).andExpect(status().isNotFound());
        mvc.perform(post("/meal-plans/" + draft + "/publish").header("Authorization", nutri()))
                .andExpect(status().isOk());
        log(patient(), UUID.randomUUID(), body(planId, 1, "DONE", "[]")).andExpect(status().isConflict());
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.plan.id").value(draft))
                .andExpect(jsonPath("$.logs.length()").value(0));
    }

    @Test
    void oRegistroFicaNaVersaoEmQueFoiFeito() throws Exception {
        UUID logId = UUID.randomUUID();
        log(patient(), logId, body(planId, 0, "DONE", "[{\"itemPosition\": 0, \"alternative\": 1}]"))
                .andExpect(status().isOk());
        // A nutricionista publica a versão 2, sem as alternativas do arroz.
        String v2 = """
                {"baseVersion": 1, "name": "Plano revisado", "startsOn": "2026-12-01", "meals": [
                  {"name": "Café", "items": [{"foodId": "%s", "amount": 100}, {"foodId": "%s", "amount": 50}]},
                  {"name": "Almoço", "items": [{"foodId": "%s", "amount": 100}]}]}
                """.formatted(rice.id(), beans.id(), beans.id());
        mvc.perform(put("/meal-plans/" + planId)
                        .header("Authorization", nutri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        // A correção continua na versão 1, onde o arroz tinha alternativas.
        log(patient(), logId, body(planId, 0, "PARTIAL", "[{\"itemPosition\": 0, \"alternative\": 1}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // O registro novo vai para a versão 2, onde a alternativa não existe mais.
        log(patient(), UUID.randomUUID(), body(planId, 1, "DONE", "[{\"itemPosition\": 0, \"alternative\": 0}]"))
                .andExpect(status().isBadRequest());
        log(patient(), UUID.randomUUID(), body(planId, 1, "DONE", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.plan.version").value(2));
    }

    @Test
    void bancoRecusaRegistroForaDasRegras() {
        UUID version = jdbc.sql("select id from meal_plan_version where plan_id = :plan")
                .param("plan", UUID.fromString(planId))
                .query(UUID.class)
                .single();
        UUID existing = UUID.randomUUID();
        insertLog(existing, version, 0, "DONE");
        expectViolation(() -> insertLog(UUID.randomUUID(), version, 1, "FEITA"), "ck_meal_log_status");
        expectViolation(() -> insertLog(UUID.randomUUID(), version, 10, "DONE"), "ck_meal_log_meal_position");
        expectViolation(() -> insertLog(UUID.randomUUID(), version, 0, "SKIPPED"), "uq_meal_log_slot");
        expectViolation(() -> insertSubstitution(existing, 30, 0), "ck_meal_log_substitution_item");
        expectViolation(() -> insertSubstitution(existing, 0, 2), "ck_meal_log_substitution_alternative");
    }

    private static void expectViolation(Runnable insert, String constraint) {
        assertThatThrownBy(insert::run)
                .as(constraint)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void insertLog(UUID id, UUID version, int meal, String status) {
        jdbc.sql("""
                        insert into meal_log (id, client_id, plan_id, version_id, version_number, meal_position,
                            logged_on, status, created_at, updated_at)
                        values (:id, :client, :plan, :version, 1, :meal, current_date, :status, now(), now())
                        """)
                .param("id", id)
                .param("client", client)
                .param("plan", UUID.fromString(planId))
                .param("version", version)
                .param("meal", meal)
                .param("status", status)
                .update();
    }

    private void insertSubstitution(UUID log, int item, int alternative) {
        jdbc.sql("insert into meal_log_substitution (log_id, item_position, alternative) values (:log, :item, :alt)")
                .param("log", log)
                .param("item", item)
                .param("alt", alternative)
                .update();
    }
}
