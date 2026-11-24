package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodMeasure;
import com.fithub.nutrition.domain.food.FoodMeasureRepository;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.plan.Meal;
import com.fithub.nutrition.domain.plan.MealItem;
import com.fithub.nutrition.domain.plan.MealPlan;
import com.fithub.nutrition.domain.plan.MealPlanRepository;
import com.fithub.nutrition.domain.plan.MealPlanStateException;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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

/** Plano alimentar com Postgres real (DEV-0081): macros, BR-10, publicação, vínculo, alimentos e as CHECKs. */
class MealPlanApiTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    FoodRepository foods;

    @Autowired
    FoodMeasureRepository measures;

    @Autowired
    MealPlanRepository plans;

    private UUID nutritionist;
    private UUID client;
    private Food rice;
    private Food beans;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>}: nutricionista; {@code personal-<sub>}: personal. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("nutri-", List.of("client", "nutritionist"), "personal-", List.of("client", "personal"));
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
        nutritionist = UUID.randomUUID();
        client = UUID.randomUUID();
        link(client, nutritionist);
        rice = food(Food.Kind.TACO, null, "Arroz, tipo 1, cozido", "128", "2.5", "28.1", "0.2", "1.6");
        beans = food(Food.Kind.TACO, null, "Feijão, carioca, cozido", "76", "4.8", "13.6", "0.5", "8.5");
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

    private Food food(
            Food.Kind kind,
            UUID owner,
            String name,
            String kcal,
            String protein,
            String carbs,
            String fat,
            String fiber) {
        Food food = new Food(
                UUID.randomUUID(),
                kind,
                owner,
                name,
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal(kcal),
                        new BigDecimal(protein),
                        new BigDecimal(carbs),
                        new BigDecimal(fat),
                        fiber == null ? null : new BigDecimal(fiber)),
                true,
                kind == Food.Kind.TACO
                        ? new Food.Source("TACO 4ª edição", UUID.randomUUID().toString())
                        : null,
                Instant.now(),
                Instant.now());
        foods.save(food);
        return food;
    }

    private FoodMeasure measure(Food food, UUID owner, String amount) {
        FoodMeasure measure =
                new FoodMeasure(UUID.randomUUID(), food.id(), owner, "Concha", new BigDecimal(amount), Instant.now());
        measures.save(measure);
        return measure;
    }

    private String nutri() {
        return "Bearer nutri-" + nutritionist;
    }

    private static String item(UUID food, String amount) {
        return "{\"foodId\": \"" + food + "\", \"amount\": " + amount + "}";
    }

    private static String meal(String name, String... items) {
        return "{\"name\": \"" + name + "\", \"suggestedTime\": \"12:30\", \"items\": [" + String.join(", ", items)
                + "]}";
    }

    private static String plan(String name, String... meals) {
        return "{\"name\": \"" + name + "\", \"startsOn\": \"2026-12-01\", \"note\": \"Beber água\", \"meals\": ["
                + String.join(", ", meals) + "]}";
    }

    private ResultActions create(String token, UUID patient, String json) throws Exception {
        return mvc.perform(post("/clients/" + patient + "/meal-plans")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions update(String token, String location, String json) throws Exception {
        return mvc.perform(put(location)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions publish(String token, String location) throws Exception {
        return mvc.perform(post(location + "/publish").header("Authorization", token));
    }

    private String locationOf(ResultActions created) throws Exception {
        return created.andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }

    private String simplePlan() throws Exception {
        return locationOf(create(nutri(), client, plan("Plano simples", meal("Almoço", item(rice.id(), "100")))));
    }

    @Test
    void macrosDoItemDaRefeicaoEDoDia() throws Exception {
        FoodMeasure ladle = measure(beans, nutritionist, "25");
        String beansByMeasure =
                "{\"foodId\": \"" + beans.id() + "\", \"measureId\": \"" + ladle.id() + "\", \"measureCount\": 2}";
        String location = locationOf(create(
                nutri(),
                client,
                plan(
                        "Plano de dezembro",
                        meal("Almoço", item(rice.id(), "100"), beansByMeasure),
                        meal("Jantar", item(beans.id(), "100")))));

        mvc.perform(get(location).header("Authorization", nutri()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.note").value("Beber água"))
                .andExpect(jsonPath("$.meals[0].suggestedTime").value("12:30"))
                .andExpect(jsonPath("$.meals[0].items[0].foodName").value("Arroz, tipo 1, cozido"))
                .andExpect(jsonPath("$.meals[0].items[0].nutrients.proteinG").value(2.5))
                // A medida: 2 conchas de 25 g são 50 g de feijão, com 2,4 g de proteína.
                .andExpect(jsonPath("$.meals[0].items[1].amount").value(50.0))
                .andExpect(jsonPath("$.meals[0].items[1].measureName").value("Concha"))
                .andExpect(jsonPath("$.meals[0].items[1].measureCount").value(2.0))
                .andExpect(jsonPath("$.meals[0].items[1].nutrients.proteinG").value(2.4))
                .andExpect(jsonPath("$.meals[0].total.proteinG").value(4.9))
                .andExpect(jsonPath("$.meals[0].total.energyKcal").value(166.0))
                .andExpect(jsonPath("$.meals[0].total.fiberG").value(5.9))
                .andExpect(jsonPath("$.meals[1].total.proteinG").value(4.8))
                .andExpect(jsonPath("$.day.proteinG").value(9.7))
                .andExpect(jsonPath("$.day.carbsG").value(48.5))
                .andExpect(jsonPath("$.day.fatG").value(1.0));
        mvc.perform(get("/clients/" + client + "/meal-plans").header("Authorization", nutri()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].day.proteinG").value(9.7));
    }

    @Test
    void publicarExigeAlimentoEmCadaRefeicaoESubstituiOAnterior() throws Exception {
        String first = locationOf(create(
                nutri(), client, plan("Plano um", meal("Café", item(rice.id(), "50")), meal("Almoço"))));
        publish(nutri(), first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("meals[1].items"));
        update(nutri(), first, plan("Plano um", meal("Café", item(rice.id(), "50"))))
                .andExpect(status().isOk());
        publish(nutri(), first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists());
        publish(nutri(), first).andExpect(status().isConflict());
        update(nutri(), first, plan("Plano um", meal("Café", item(rice.id(), "60"))))
                .andExpect(status().isConflict());

        String second = simplePlan();
        publish(nutri(), second).andExpect(status().isOk());
        mvc.perform(get(first).header("Authorization", nutri()))
                .andExpect(jsonPath("$.status").value("REPLACED"));
        mvc.perform(get(second).header("Authorization", nutri()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void semVinculoOuDeOutraNutricionistaResponde404EOPersonalNaoTemRota() throws Exception {
        String location = simplePlan();
        String stranger = "Bearer nutri-" + UUID.randomUUID();
        String body = plan("Plano", meal("Almoço", item(rice.id(), "100")));

        mvc.perform(get("/clients/" + client + "/meal-plans").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        create(stranger, client, body).andExpect(status().isNotFound());
        mvc.perform(get(location).header("Authorization", stranger)).andExpect(status().isNotFound());
        update(stranger, location, body).andExpect(status().isNotFound());
        publish(stranger, location).andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + client + "/meal-plans")
                        .header("Authorization", "Bearer personal-" + nutritionist))
                .andExpect(status().isForbidden());
    }

    @Test
    void autoraSemVinculoAindaLeOPlanoMasNaoEditaNemPublica() throws Exception {
        String location = simplePlan();
        jdbc.sql("update client_link set status = 'ENDED', ended_at = now() where client_id = :client")
                .param("client", client)
                .update();

        mvc.perform(get(location).header("Authorization", nutri())).andExpect(status().isOk());
        update(nutri(), location, plan("Plano", meal("Almoço", item(rice.id(), "100"))))
                .andExpect(status().isNotFound());
        publish(nutri(), location).andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + client + "/meal-plans").header("Authorization", nutri()))
                .andExpect(status().isNotFound());
        create(nutri(), client, plan("Plano", meal("Almoço", item(rice.id(), "100"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void alimentoOuMedidaIndisponivelResponde400NoCampo() throws Exception {
        UUID other = UUID.randomUUID();
        Food othersOwn = food(Food.Kind.OWN, other, "Granola dela", "400", "10", "60", "15", "8");
        create(nutri(), client, plan("Plano", meal("Café", item(rice.id(), "10"), item(othersOwn.id(), "40"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("meals[0].items[1].foodId"));
        create(nutri(), client, plan("Plano", meal("Café", item(UUID.randomUUID(), "40"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("meals[0].items[0].foodId"));

        FoodMeasure othersMeasure = measure(beans, other, "25");
        FoodMeasure riceMeasure = measure(rice, nutritionist, "25");
        for (FoodMeasure wrong : List.of(othersMeasure, riceMeasure)) {
            String byMeasure =
                    "{\"foodId\": \"" + beans.id() + "\", \"measureId\": \"" + wrong.id() + "\", \"measureCount\": 2}";
            create(nutri(), client, plan("Plano", meal("Café", byMeasure)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("meals[0].items[0].measureId"));
        }
    }

    @Test
    void alimentoArquivadoSoSeJaEstavaNoPlano() throws Exception {
        Food granola = food(Food.Kind.OWN, nutritionist, "Granola da casa", "400", "10", "60", "15", "8");
        String location = locationOf(create(nutri(), client, plan("Plano", meal("Café", item(granola.id(), "40")))));
        foods.save(granola.archive(Instant.now()));

        update(nutri(), location, plan("Plano", meal("Café", item(granola.id(), "30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].items[0].amount").value(30.0));
        create(nutri(), client, plan("Plano", meal("Café", item(granola.id(), "40"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("meals[0].items[0].foodId"));
    }

    @Test
    void camposForaDasRegrasApontamOCaminho() throws Exception {
        String ok = item(rice.id(), "100");
        record Case(String json, String field) {}
        List<String> thirtyOne = Collections.nCopies(31, ok);
        List<String> elevenMeals = new ArrayList<>(Collections.nCopies(11, meal("Lanche", ok)));
        for (Case wrong : List.of(
                new Case(plan("a", meal("Almoço", ok)), "name"),
                new Case(plan("Plano"), "meals"),
                new Case(plan("Plano", elevenMeals.toArray(String[]::new)), "meals"),
                new Case(plan("Plano", meal("x", ok)), "meals[0].name"),
                new Case(plan("Plano", "null"), "meals[0]"),
                new Case(plan("Plano", "{\"name\": \"Café\", \"items\": [null]}"), "meals[0].items[0].foodId"),
                new Case(
                        plan("Plano", "{\"name\": \"Café\", \"items\": [{\"amount\": 10}]}"),
                        "meals[0].items[0].foodId"),
                new Case(
                        plan("Plano", meal("Café", ok), meal("Almoço", item(rice.id(), "0"))),
                        "meals[1].items[0].amount"),
                new Case(plan("Plano", meal("Café", item(rice.id(), "5000.1"))), "meals[0].items[0].amount"),
                new Case(plan("Plano", meal("Café", thirtyOne.toArray(String[]::new))), "meals[0].items"),
                new Case(
                        plan("Plano", "{\"name\": \"Café\", \"suggestedTime\": \"7h\", \"items\": [" + ok + "]}"),
                        "meals[0].suggestedTime"),
                new Case("{\"name\": \"Plano\", \"meals\": [" + meal("Café", ok) + "]}", "startsOn"))) {
            create(nutri(), client, wrong.json())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(wrong.field()));
        }
        FoodMeasure ladle = measure(beans, nutritionist, "25");
        String tooMany =
                "{\"foodId\": \"" + beans.id() + "\", \"measureId\": \"" + ladle.id() + "\", \"measureCount\": 101}";
        create(nutri(), client, plan("Plano", meal("Café", tooMany)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("meals[0].items[0].measureCount"));
        // 10 refeições e 30 alimentos numa refeição cabem.
        List<String> tenMeals = new ArrayList<>(Collections.nCopies(10, meal("Lanche", ok)));
        tenMeals.set(0, meal("Café", Collections.nCopies(30, ok).toArray(String[]::new)));
        create(nutri(), client, plan("Plano", tenMeals.toArray(String[]::new))).andExpect(status().isCreated());
    }

    private static UUID idOf(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void rascunhoLidoAntesDaPublicacaoNaoRegravaOPlanoPublicado() throws Exception {
        String location = simplePlan();
        MealPlan stale = plans.findById(idOf(location)).orElseThrow();
        publish(nutri(), location).andExpect(status().isOk());

        MealPlan staleEdit = stale.editDraft(
                "Editado no meio",
                LocalDate.of(2026, 12, 2),
                null,
                List.of(new Meal.Draft(
                        "Ceia", null, null, List.of(new MealItem(beans.id(), BigDecimal.TEN, null, null)))),
                Instant.now());
        assertThatThrownBy(() -> plans.saveDraft(staleEdit, stale.updatedAt()))
                .isInstanceOf(MealPlanStateException.class);
        mvc.perform(get(location).header("Authorization", nutri()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.name").value("Plano simples"))
                .andExpect(jsonPath("$.meals[0].name").value("Almoço"))
                .andExpect(jsonPath("$.meals[0].items[0].amount").value(100.0));
    }

    @Test
    void publicacaoLidaAntesDaEdicaoNaoPublicaOConteudoAntigo() throws Exception {
        String location = simplePlan();
        MealPlan stale = plans.findById(idOf(location)).orElseThrow();
        update(nutri(), location, plan("Plano editado", meal("Almoço", item(rice.id(), "100")), meal("Jantar")))
                .andExpect(status().isOk());

        MealPlan stalePublished = stale.publish(Instant.now());
        assertThatThrownBy(() -> plans.saveStatus(stalePublished, stale.updatedAt()))
                .isInstanceOf(MealPlanStateException.class);
        mvc.perform(get(location).header("Authorization", nutri()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.name").value("Plano editado"))
                .andExpect(jsonPath("$.meals.length()").value(2));
    }

    private static String withAlternatives(UUID food, String amount, String... alternatives) {
        return "{\"foodId\": \"" + food + "\", \"amount\": " + amount + ", \"alternatives\": ["
                + String.join(", ", alternatives) + "]}";
    }

    private static String alternative(UUID food, String amount) {
        return "{\"foodId\": \"" + food + "\", \"amount\": " + amount + "}";
    }

    @Test
    void alternativasMostramADiferencaDeCadaMacroComSinalEFicamForaDaSoma() throws Exception {
        Food pasta = food(Food.Kind.TACO, null, "Macarrão, trigo, cozido", "102", "3.4", "19.9", "1.2", "1.5");
        String location = locationOf(create(
                nutri(),
                client,
                plan(
                        "Plano com trocas",
                        meal(
                                "Almoço",
                                withAlternatives(
                                        rice.id(),
                                        "100",
                                        alternative(pasta.id(), "80"),
                                        alternative(beans.id(), "50"))))));

        mvc.perform(get(location).header("Authorization", nutri()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].items[0].alternatives.length()").value(2))
                .andExpect(
                        jsonPath("$.meals[0].items[0].alternatives[0].foodName").value("Macarrão, trigo, cozido"))
                .andExpect(
                        jsonPath("$.meals[0].items[0].alternatives[0].amount").value(80.0))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[0].nutrients.proteinG")
                        .value(2.7))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[0].difference.energyKcal")
                        .value(-46.4))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[0].difference.proteinG")
                        .value(0.2))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[0].difference.carbsG")
                        .value(-12.2))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[0].difference.fatG")
                        .value(0.8))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[1].difference.energyKcal")
                        .value(-90.0))
                .andExpect(jsonPath("$.meals[0].items[0].alternatives[1].difference.proteinG")
                        .value(-0.1))
                // A refeição soma só o principal: a alternativa é uma troca.
                .andExpect(jsonPath("$.meals[0].total.energyKcal").value(128.0))
                .andExpect(jsonPath("$.day.proteinG").value(2.5));
    }

    @Test
    void terceiraRepetidaOuDeOutraNutricionistaResponde400NoCampo() throws Exception {
        Food pasta = food(Food.Kind.TACO, null, "Macarrão, trigo, cozido", "102", "3.4", "19.9", "1.2", "1.5");
        Food othersOwn = food(Food.Kind.OWN, UUID.randomUUID(), "Granola dela", "400", "10", "60", "15", "8");
        Food potato = food(Food.Kind.TACO, null, "Batata, inglesa, cozida", "52", "1.2", "11.9", "0", "1.3");
        String pastaAlt = alternative(pasta.id(), "80");
        record Case(String item, String field) {}
        for (Case wrong : List.of(
                new Case(
                        withAlternatives(
                                rice.id(),
                                "100",
                                pastaAlt,
                                alternative(beans.id(), "50"),
                                alternative(potato.id(), "90")),
                        "meals[0].items[0].alternatives"),
                new Case(
                        withAlternatives(rice.id(), "100", alternative(rice.id(), "80")),
                        "meals[0].items[0].alternatives[0].foodId"),
                new Case(
                        withAlternatives(rice.id(), "100", pastaAlt, alternative(pasta.id(), "60")),
                        "meals[0].items[0].alternatives[1].foodId"),
                new Case(
                        withAlternatives(rice.id(), "100", pastaAlt, alternative(othersOwn.id(), "40")),
                        "meals[0].items[0].alternatives[1].foodId"),
                new Case(
                        withAlternatives(rice.id(), "100", alternative(UUID.randomUUID(), "40")),
                        "meals[0].items[0].alternatives[0].foodId"),
                new Case(
                        withAlternatives(rice.id(), "100", alternative(pasta.id(), "0")),
                        "meals[0].items[0].alternatives[0].amount"))) {
            create(nutri(), client, plan("Plano", meal("Almoço", wrong.item())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(wrong.field()));
        }
    }

    @Test
    void alternativaArquivadaSoSeJaEstavaNoPlano() throws Exception {
        Food granola = food(Food.Kind.OWN, nutritionist, "Granola da casa", "400", "10", "60", "15", "8");
        String location = locationOf(create(
                nutri(),
                client,
                plan("Plano", meal("Café", withAlternatives(rice.id(), "100", alternative(granola.id(), "40"))))));
        foods.save(granola.archive(Instant.now()));

        update(
                        nutri(),
                        location,
                        plan("Plano", meal("Café", withAlternatives(rice.id(), "90", alternative(granola.id(), "30")))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.meals[0].items[0].alternatives[0].amount").value(30.0));
        create(
                        nutri(),
                        client,
                        plan(
                                "Plano",
                                meal("Café", withAlternatives(rice.id(), "100", alternative(granola.id(), "40")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("meals[0].items[0].alternatives[0].foodId"));
    }

    @Test
    void bancoRecusaAlternativaForaDasRegras() {
        UUID plan = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        insertPlan(plan, client, "DRAFT");
        insertVersion(version, plan, 1, "Plano");
        insertMeal(version, 0, "Café");
        insertItem(version, 0, "10", null);

        expectViolation(() -> insertAlternative(version, 2, "10"), "ck_meal_item_alternative_position");
        expectViolation(() -> insertAlternative(version, 0, "0.0"), "ck_meal_item_alternative_amount");
        expectViolation(() -> insertAlternative(version, 0, "5000.1"), "ck_meal_item_alternative_amount");
    }

    private void insertAlternative(UUID version, int position, String amount) {
        jdbc.sql("""
                        insert into meal_item_alternative
                            (version_id, meal_position, item_position, position, food_id, amount)
                        values (:version, 0, 0, :position, :food, :amount)
                        """)
                .param("version", version)
                .param("position", position)
                .param("food", beans.id())
                .param("amount", new BigDecimal(amount))
                .update();
    }

    @Test
    void bancoRecusaPlanoForaDasRegras() {
        UUID plan = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        insertPlan(plan, client, "DRAFT");
        insertVersion(version, plan, 1, "Plano");
        insertMeal(version, 0, "Café");

        expectViolation(() -> insertPlan(UUID.randomUUID(), client, "ATIVO"), "ck_meal_plan_status");
        insertPlan(UUID.randomUUID(), client, "PUBLISHED");
        expectViolation(() -> insertPlan(UUID.randomUUID(), client, "PUBLISHED"), "ux_meal_plan_published");
        expectViolation(() -> insertVersion(UUID.randomUUID(), plan, 0, "Plano"), "ck_meal_plan_version_number");
        expectViolation(() -> insertVersion(UUID.randomUUID(), plan, 2, " a "), "ck_meal_plan_version_name");
        expectViolation(() -> insertMeal(version, 10, "Lanche"), "ck_meal_position");
        expectViolation(() -> insertMeal(version, 1, " a "), "ck_meal_name");
        expectViolation(() -> insertItem(version, 30, "10", null), "ck_meal_item_position");
        expectViolation(() -> insertItem(version, 0, "0.0", null), "ck_meal_item_amount");
        expectViolation(() -> insertItem(version, 0, "5000.1", null), "ck_meal_item_amount");
        expectViolation(() -> insertItem(version, 0, "10", "100.1"), "ck_meal_item_measure");
        expectViolation(() -> insertItem(version, 0, "10", "0.0"), "ck_meal_item_measure");
    }

    private static void expectViolation(Runnable insert, String constraint) {
        assertThatThrownBy(insert::run)
                .as(constraint)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void insertPlan(UUID id, UUID patient, String status) {
        jdbc.sql("""
                        insert into meal_plan (id, client_id, nutritionist_id, status, created_at, updated_at)
                        values (:id, :client, :nutritionist, :status, now(), now())
                        """)
                .param("id", id)
                .param("client", patient)
                .param("nutritionist", nutritionist)
                .param("status", status)
                .update();
    }

    private void insertVersion(UUID id, UUID plan, int number, String name) {
        jdbc.sql("""
                        insert into meal_plan_version (id, plan_id, number, name, starts_on, created_at)
                        values (:id, :plan, :number, :name, current_date, now())
                        """)
                .param("id", id)
                .param("plan", plan)
                .param("number", number)
                .param("name", name)
                .update();
    }

    private void insertMeal(UUID version, int position, String name) {
        jdbc.sql("insert into meal (version_id, position, name) values (:version, :position, :name)")
                .param("version", version)
                .param("position", position)
                .param("name", name)
                .update();
    }

    private void insertItem(UUID version, int position, String amount, String measureCount) {
        jdbc.sql("""
                        insert into meal_item (version_id, meal_position, position, food_id, amount, measure_count)
                        values (:version, 0, :position, :food, :amount, :measureCount)
                        """)
                .param("version", version)
                .param("position", position)
                .param("food", rice.id())
                .param("amount", new BigDecimal(amount))
                .param("measureCount", measureCount == null ? null : new BigDecimal(measureCount))
                .update();
    }
}
