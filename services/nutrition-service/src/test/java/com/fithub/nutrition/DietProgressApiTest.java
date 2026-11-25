package com.fithub.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

/** Evolução da dieta (DEV-0092) com Postgres real: aderência, alternativas, desvios, autoria, acesso e a atividade. */
class DietProgressApiTest extends ServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    FoodRepository foods;

    private UUID nutritionist;
    private UUID client;
    private Food rice;
    private Food pasta;
    private Food beans;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>}: nutricionista; {@code personal-<sub>}: personal; {@code conta-<sub>}: cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix = Map.of(
                    "nutri-",
                    List.of("client", "nutritionist"),
                    "personal-",
                    List.of("client", "personal"),
                    "conta-",
                    List.of("client"));
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

    private void link(UUID patient, UUID professional) {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :professional, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", patient)
                .param("professional", professional)
                .update();
    }

    private Food food(String name) {
        Food food = new Food(
                UUID.randomUUID(),
                Food.Kind.TACO,
                null,
                name,
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("100"), new BigDecimal("3"), new BigDecimal("20"), new BigDecimal("1"), null),
                true,
                new Food.Source("TACO 4ª edição", UUID.randomUUID().toString()),
                Instant.now(),
                Instant.now());
        foods.save(food);
        return food;
    }

    @BeforeEach
    void setUp() {
        nutritionist = UUID.randomUUID();
        client = UUID.randomUUID();
        link(client, nutritionist);
        rice = food("Arroz, tipo 1, cozido");
        pasta = food("Macarrão, trigo, cozido");
        beans = food("Feijão, carioca, cozido");
    }

    /** Plano de 4 refeições da nutricionista, publicado há 10 dias: o café tem arroz com macarrão e feijão de alternativa. */
    private UUID publishedPlan(UUID author) throws Exception {
        return publishedPlan(author, 10);
    }

    /** O mesmo plano, publicado há {@code daysAgo} dias. */
    private UUID publishedPlan(UUID author, int daysAgo) throws Exception {
        String other = "{\"name\": \"%s\", \"items\": [{\"foodId\": \"" + beans.id() + "\", \"amount\": 50}]}";
        String body = "{\"name\": \"Plano\", \"startsOn\": \"" + TODAY + "\", \"meals\": ["
                + "{\"name\": \"Café\", \"items\": [{\"foodId\": \"" + rice.id()
                + "\", \"amount\": 100, \"alternatives\": ["
                + "{\"foodId\": \"" + pasta.id() + "\", \"amount\": 80}, {\"foodId\": \"" + beans.id()
                + "\", \"amount\": 50}]}]}, "
                + other.formatted("Almoço") + ", " + other.formatted("Lanche") + ", " + other.formatted("Jantar")
                + "]}";
        String location = mvc.perform(post("/clients/" + client + "/meal-plans")
                        .header("Authorization", "Bearer nutri-" + author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        UUID planId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        mvc.perform(post("/meal-plans/" + planId + "/publish").header("Authorization", "Bearer nutri-" + author))
                .andExpect(status().isOk());
        jdbc.sql(
                        "update meal_plan_version set published_at = published_at - make_interval(days => :days) where plan_id = :plan")
                .param("days", daysAgo)
                .param("plan", planId)
                .update();
        return planId;
    }

    /** Um registro de refeição há {@code daysAgo} dias, com a alternativa usada no primeiro item (ou nenhuma). */
    private void logged(
            UUID planId, int daysAgo, int meal, String status, Integer alternative, String deviation, String photo) {
        UUID log = UUID.randomUUID();
        UUID version = jdbc.sql("select id from meal_plan_version where plan_id = :plan order by number desc limit 1")
                .param("plan", planId)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into meal_log (id, client_id, plan_id, version_id, version_number, meal_position, logged_on,
                            status, deviation, photo_key, photo_uploaded_at, created_at, updated_at)
                        values (:id, :client, :plan, :version, 1, :meal, :day, :status, :deviation, :photo,
                            case when cast(:photo as varchar) is null then null else now() end,
                            now() - make_interval(days => :daysAgo), now())
                        """)
                .param("id", log)
                .param("client", client)
                .param("plan", planId)
                .param("version", version)
                .param("meal", meal)
                .param("day", TODAY.minusDays(daysAgo))
                .param("status", status)
                .param("deviation", deviation)
                .param("photo", photo)
                .param("daysAgo", daysAgo)
                .update();
        if (alternative != null) {
            jdbc.sql("insert into meal_log_substitution (log_id, item_position, alternative) values (:log, 0, :alt)")
                    .param("log", log)
                    .param("alt", alternative)
                    .update();
        }
    }

    private String period() {
        return "?from=" + TODAY.minusDays(2) + "&to=" + TODAY;
    }

    @Test
    void aderenciaAlternativasEDesviosDoPaciente() throws Exception {
        UUID plan = publishedPlan(nutritionist);
        logged(plan, 2, 0, "DONE", 0, null, null);
        logged(plan, 1, 0, "DONE", 1, null, null);
        logged(plan, 1, 1, "PARTIAL", null, null, null);
        logged(plan, 0, 0, "DONE", 0, null, null);
        logged(plan, 0, 1, "PARTIAL", null, null, null);
        logged(plan, 0, 2, "SKIPPED", null, "Pulei o lanche", "meal-logs/" + client + "/x/photo-1");
        // Fora do período: não conta.
        logged(plan, 5, 0, "DONE", 0, "Comi fora", null);

        mvc.perform(get("/clients/" + client + "/progress/nutrition" + period())
                        .header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherence.done").value(4.0))
                .andExpect(jsonPath("$.adherence.prescribed").value(12))
                .andExpect(jsonPath("$.adherence.percent").value(33.3))
                .andExpect(jsonPath("$.adherence.days.length()").value(3))
                .andExpect(jsonPath("$.adherence.days[1].done").value(1.5))
                .andExpect(jsonPath("$.adherence.days[1].prescribed").value(4))
                .andExpect(jsonPath("$.alternatives.length()").value(2))
                .andExpect(jsonPath("$.alternatives[0].foodName").value("Macarrão, trigo, cozido"))
                .andExpect(jsonPath("$.alternatives[0].times").value(2))
                .andExpect(jsonPath("$.alternatives[1].foodName").value("Feijão, carioca, cozido"))
                .andExpect(jsonPath("$.alternatives[1].times").value(1))
                .andExpect(jsonPath("$.deviations.length()").value(1))
                .andExpect(jsonPath("$.deviations[0].mealName").value("Lanche"))
                .andExpect(jsonPath("$.deviations[0].deviation").value("Pulei o lanche"))
                .andExpect(jsonPath("$.deviations[0].photoUrl").exists());
        mvc.perform(get("/me/progress/nutrition" + period()).header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherence.done").value(4.0));
    }

    @Test
    void aNutricionistaVeSoOsPlanosDelaEOPacienteVeTodos() throws Exception {
        UUID first = publishedPlan(nutritionist);
        logged(first, 2, 0, "DONE", null, null, null);
        // Outra nutricionista vinculada depois publica o dela: o primeiro fica substituído hoje.
        UUID other = UUID.randomUUID();
        link(client, other);
        // O plano novo foi publicado no mesmo dia em que substituiu o primeiro, há 2 dias.
        UUID second = publishedPlan(other, 2);
        logged(second, 0, 0, "DONE", null, null, null);
        // O primeiro foi substituído há 2 dias: só prescreve até lá.
        jdbc.sql("update meal_plan set updated_at = now() - interval '2 days' where id = :id")
                .param("id", first)
                .update();

        mvc.perform(get("/clients/" + client + "/progress/nutrition" + period())
                        .header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(jsonPath("$.adherence.done").value(1.0))
                .andExpect(jsonPath("$.adherence.prescribed").value(4));
        mvc.perform(get("/clients/" + client + "/progress/nutrition" + period())
                        .header("Authorization", "Bearer nutri-" + other))
                .andExpect(jsonPath("$.adherence.done").value(1.0))
                .andExpect(jsonPath("$.adherence.prescribed").value(12));
        // O paciente vê os dois planos.
        mvc.perform(get("/me/progress/nutrition" + period()).header("Authorization", "Bearer conta-" + client))
                // No dia da troca prescreve só o plano novo: 3 dias × 4, sem somar o substituído (Code Review do #277).
                .andExpect(jsonPath("$.adherence.done").value(2.0))
                .andExpect(jsonPath("$.adherence.prescribed").value(12))
                .andExpect(jsonPath("$.adherence.days[0].prescribed").value(4))
                .andExpect(jsonPath("$.adherence.days[0].done").value(1.0));
        // A última atividade da primeira nutricionista é o registro no plano dela, não o de hoje no da outra.
        mvc.perform(get("/professionals/me/clients/activity").header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(jsonPath("$[0].lastActivityAt")
                        .value(org.hamcrest.Matchers.startsWith(
                                LocalDate.now(ZoneOffset.UTC).minusDays(2).toString())));
    }

    @Test
    void semVinculoPeriodoInvalidoEPapeis() throws Exception {
        mvc.perform(get("/clients/" + client + "/progress/nutrition")
                        .header("Authorization", "Bearer nutri-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + client + "/progress/nutrition")
                        .header("Authorization", "Bearer personal-" + nutritionist))
                .andExpect(status().isForbidden());
        mvc.perform(get("/clients/" + client + "/progress/nutrition?from=" + TODAY + "&to=" + TODAY.minusDays(1))
                        .header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        mvc.perform(get("/me/progress/nutrition?from=" + TODAY.minusDays(182))
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        mvc.perform(get("/me/progress/nutrition").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(TODAY.minusDays(27).toString()))
                .andExpect(jsonPath("$.adherence.percent").doesNotExist());
    }

    @Test
    void ultimaAtividadeDeCadaPacienteVinculado() throws Exception {
        UUID plan = publishedPlan(nutritionist);
        logged(plan, 3, 0, "DONE", null, null, null);
        logged(plan, 1, 1, "DONE", null, null, null);
        UUID idle = UUID.randomUUID();
        link(idle, nutritionist);
        link(UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(get("/professionals/me/clients/activity").header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clientId").value(client.toString()))
                .andExpect(jsonPath("$[0].lastActivityAt").exists())
                .andExpect(jsonPath("$[1].clientId").value(idle.toString()))
                .andExpect(jsonPath("$[1].lastActivityAt").doesNotExist());
        mvc.perform(get("/professionals/me/clients/activity")
                        .header("Authorization", "Bearer personal-" + nutritionist))
                .andExpect(status().isForbidden());
        // Nutricionista sem paciente vinculado: lista vazia, não erro.
        mvc.perform(get("/professionals/me/clients/activity")
                        .header("Authorization", "Bearer nutri-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
