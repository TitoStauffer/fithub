package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** Alimento com Postgres real (DEV-0074): leitura pela nutricionista, visibilidade do próprio e as CHECKs. */
class FoodApiTest extends ServiceIntegrationTest {

    @Autowired
    FoodRepository foods;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

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

    private static Food.Nutrients nutrients() {
        return new Food.Nutrients(
                new BigDecimal("128"), new BigDecimal("2.5"), new BigDecimal("28.1"), new BigDecimal("0.2"), null);
    }

    private Food taco() {
        Food food = new Food(
                UUID.randomUUID(),
                Food.Kind.TACO,
                null,
                "Arroz, tipo 1, cozido",
                Food.BaseUnit.G,
                nutrients(),
                true,
                new Food.Source("TACO 4ª edição", UUID.randomUUID().toString()),
                Instant.now(),
                Instant.now());
        foods.save(food);
        return food;
    }

    private Food ownOf(UUID owner) {
        Food food = new Food(
                UUID.randomUUID(),
                Food.Kind.OWN,
                owner,
                "Granola da casa",
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("400"),
                        new BigDecimal("10"),
                        new BigDecimal("60"),
                        new BigDecimal("15"),
                        new BigDecimal("8")),
                false,
                null,
                Instant.now(),
                Instant.now());
        foods.save(food);
        return food;
    }

    @Test
    void nutricionistaLeATacoEOProprio() throws Exception {
        UUID me = UUID.randomUUID();
        Food taco = taco();
        Food mine = ownOf(me);

        mvc.perform(get("/foods/" + taco.id()).header("Authorization", "Bearer nutri-" + me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TACO"))
                .andExpect(jsonPath("$.name").value("Arroz, tipo 1, cozido"))
                .andExpect(jsonPath("$.baseUnit").value("G"))
                .andExpect(jsonPath("$.energyKcal").value(128.0))
                .andExpect(jsonPath("$.proteinG").value(2.5))
                .andExpect(jsonPath("$.carbsG").value(28.1))
                .andExpect(jsonPath("$.fatG").value(0.2))
                .andExpect(jsonPath("$.fiberG").doesNotExist())
                .andExpect(jsonPath("$.vegan").value(true))
                .andExpect(jsonPath("$.source.name").value("TACO 4ª edição"));
        mvc.perform(get("/foods/" + mine.id()).header("Authorization", "Bearer nutri-" + me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("OWN"))
                .andExpect(jsonPath("$.fiberG").value(8.0))
                .andExpect(jsonPath("$.vegan").value(false))
                .andExpect(jsonPath("$.source").doesNotExist());
    }

    @Test
    void oProprioDeOutraNutricionistaRespondeComoInexistente() throws Exception {
        Food theirs = ownOf(UUID.randomUUID());

        mvc.perform(get("/foods/" + theirs.id()).header("Authorization", "Bearer nutri-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/foods/" + UUID.randomUUID()).header("Authorization", "Bearer nutri-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void soONutricionistaLeAlimentos() throws Exception {
        Food taco = taco();

        mvc.perform(get("/foods/" + taco.id()).header("Authorization", "Bearer personal-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/foods/" + taco.id()).header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/foods/" + taco.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void bancoRecusaAlimentoForaDasRegras() {
        String insert = """
                insert into food (id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g, fiber_g,
                    vegan, external_source, external_id, created_at, updated_at)
                values (:id, :kind, :owner, :name, :unit, :kcal, :protein, :carbs, :fat, :fiber, true, :source, :external,
                    now(), now())
                """;
        UUID owner = UUID.randomUUID();
        record Row(
                String kind,
                UUID owner,
                String name,
                String unit,
                String kcal,
                String protein,
                String carbs,
                String fat,
                String fiber,
                String source,
                String constraint) {}
        for (Row row : new Row[] {
            new Row("XX", owner, "Arroz", "G", "100", "1", "1", "1", null, null, "ck_food_kind"),
            new Row("OWN", null, "Arroz", "G", "100", "1", "1", "1", null, null, "ck_food_owner"),
            new Row("TACO", null, "Arroz", "G", "100", "1", "1", "1", null, null, "ck_food_source"),
            new Row("OWN", owner, "Arroz", "G", "100", "1", "1", "1", null, "TACO", "ck_food_source"),
            new Row("OWN", owner, " a ", "G", "100", "1", "1", "1", null, null, "ck_food_name"),
            new Row("OWN", owner, "Arroz", "KG", "100", "1", "1", "1", null, null, "ck_food_base_unit"),
            new Row("OWN", owner, "Arroz", "G", "900.1", "1", "1", "1", null, null, "ck_food_energy"),
            new Row("OWN", owner, "Arroz", "G", "100", "-1", "1", "1", null, null, "ck_food_macros"),
            new Row("OWN", owner, "Arroz", "G", "100", "40", "40", "20.01", null, null, "ck_food_macros"),
            new Row("OWN", owner, "Arroz", "G", "100", "1", "1", "1", "100.01", null, "ck_food_fiber")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("kind", row.kind())
                            .param("owner", row.owner(), Types.OTHER)
                            .param("name", row.name())
                            .param("unit", row.unit())
                            .param("kcal", new BigDecimal(row.kcal()))
                            .param("protein", new BigDecimal(row.protein()))
                            .param("carbs", new BigDecimal(row.carbs()))
                            .param("fat", new BigDecimal(row.fat()))
                            .param("fiber", row.fiber() == null ? null : new BigDecimal(row.fiber()), Types.NUMERIC)
                            .param("source", row.source())
                            .param("external", row.source() == null ? null : "1")
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
    }

    private String measure(String name, String amount) {
        return "{\"name\": \"" + name + "\", \"amount\": " + amount + "}";
    }

    private ResultActions addMeasure(UUID food, String token, String body) throws Exception {
        return mvc.perform(post("/foods/" + food + "/measures")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void medidaCaseiraESoDaNutricionistaQueCadastrou() throws Exception {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Food taco = taco();

        String location = addMeasure(taco.id(), "nutri-" + me, measure("Colher de sopa", "25"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Colher de sopa"))
                .andExpect(jsonPath("$.amount").value(25.0))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        mvc.perform(get("/foods/" + taco.id()).header("Authorization", "Bearer nutri-" + me))
                .andExpect(jsonPath("$.measures.length()").value(1))
                .andExpect(jsonPath("$.measures[0].name").value("Colher de sopa"));
        // Outra nutricionista vê o alimento da TACO, mas não a medida.
        mvc.perform(get("/foods/" + taco.id()).header("Authorization", "Bearer nutri-" + other))
                .andExpect(jsonPath("$.measures").isEmpty());
        mvc.perform(delete(location).header("Authorization", "Bearer nutri-" + other))
                .andExpect(status().isNotFound());
        mvc.perform(delete(location).header("Authorization", "Bearer nutri-" + me))
                .andExpect(status().isNoContent());
        mvc.perform(get("/foods/" + taco.id()).header("Authorization", "Bearer nutri-" + me))
                .andExpect(jsonPath("$.measures").isEmpty());
    }

    @Test
    void medidaForaDasRegrasNomeRepetidoEMaisDeDez() throws Exception {
        UUID me = UUID.randomUUID();
        Food taco = taco();
        String token = "nutri-" + me;

        addMeasure(taco.id(), token, measure("Fatia", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        addMeasure(taco.id(), token, measure("a", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        addMeasure(taco.id(), token, measure("Fatia", "30")).andExpect(status().isCreated());
        addMeasure(taco.id(), token, measure("FATIA", "40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        for (int index = 2; index <= 10; index++) {
            addMeasure(taco.id(), token, measure("Medida " + index, "10")).andExpect(status().isCreated());
        }
        addMeasure(taco.id(), token, measure("Medida 11", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("measures"));
        // Alimento próprio de outra nutricionista: não existe para ela.
        addMeasure(ownOf(UUID.randomUUID()).id(), token, measure("Fatia", "30")).andExpect(status().isNotFound());
    }

    @Test
    void bancoRecusaMedidaForaDasRegrasENomeRepetido() {
        Food taco = taco();
        UUID owner = UUID.randomUUID();
        String insert = """
                insert into food_measure (id, food_id, owner_id, name, amount, created_at)
                values (:id, :food, :owner, :name, :amount, now())
                """;
        record Row(String name, String amount, String constraint) {}
        for (Row row : new Row[] {
            new Row(" a ", "10", "ck_food_measure_name"),
            new Row("Fatia", "0.0", "ck_food_measure_amount"),
            new Row("Fatia", "2000.1", "ck_food_measure_amount")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("food", taco.id())
                            .param("owner", owner)
                            .param("name", row.name())
                            .param("amount", new BigDecimal(row.amount()))
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }
        jdbc.sql(insert)
                .param("id", UUID.randomUUID())
                .param("food", taco.id())
                .param("owner", owner)
                .param("name", "Fatia")
                .param("amount", new BigDecimal("30"))
                .update();
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("food", taco.id())
                        .param("owner", owner)
                        .param("name", "fatia")
                        .param("amount", new BigDecimal("30"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_food_measure_name");
    }

    private static final String GRANOLA = """
            {"name": "Granola da casa", "baseUnit": "G", "energyKcal": 400, "proteinG": 10, "carbsG": 60,
             "fatG": 15, "fiberG": 8, "vegan": true}
            """;

    private ResultActions createFood(String token, String body) throws Exception {
        return mvc.perform(post("/foods")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String idOf(ResultActions created) throws Exception {
        String location = created.andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring("/foods/".length());
    }

    private Food tacoWith(String name, String protein, boolean vegan) {
        Food food = new Food(
                UUID.randomUUID(),
                Food.Kind.TACO,
                null,
                name,
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("100"),
                        new BigDecimal(protein),
                        new BigDecimal("20"),
                        new BigDecimal("1"),
                        null),
                vegan,
                new Food.Source("TACO 4ª edição", UUID.randomUUID().toString()),
                Instant.now(),
                Instant.now());
        foods.save(food);
        return food;
    }

    private static String recipe(String name, UUID first, String firstAmount, UUID second, String secondAmount) {
        return """
                {"name": "%s", "recipe": {"yieldGrams": 300, "ingredients": [
                  {"foodId": "%s", "amount": %s}, {"foodId": "%s", "amount": %s}]}}
                """.formatted(name, first, firstAmount, second, secondAmount);
    }

    @Test
    void nutricionistaCriaEditaEArquivaOProprioEOutraRecebe404() throws Exception {
        UUID me = UUID.randomUUID();
        String token = "nutri-" + me;
        String other = "nutri-" + UUID.randomUUID();
        String id = idOf(createFood(token, GRANOLA)
                .andExpect(jsonPath("$.kind").value("OWN"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(content().string(Matchers.not(Matchers.containsString("\"recipe\"")))));

        mvc.perform(get("/foods/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/foods/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANOLA.replace("Granola da casa", "Granola sem açúcar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Granola sem açúcar"));
        mvc.perform(put("/foods/" + id)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANOLA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/foods/" + id + "/archive").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/foods/" + id + "/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        // A dona continua lendo o arquivado; o da TACO não é editável por ninguém.
        mvc.perform(get("/foods/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.archived").value(true));
        mvc.perform(put("/foods/" + taco().id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANOLA))
                .andExpect(status().isNotFound());
        createFood(token, GRANOLA.replace("\"proteinG\": 10", "\"proteinG\": 50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("per100"));
    }

    @Test
    void receitaCalculaOsValoresPor100EEVeganaSoComTodosOsIngredientesVeganos() throws Exception {
        String token = "nutri-" + UUID.randomUUID();
        Food rice = tacoWith("Arroz, tipo 1, cozido", "2.5", true);
        Food beans = tacoWith("Feijão, carioca, cozido", "4.8", true);
        Food egg = tacoWith("Ovo, de galinha, inteiro, cozido", "13.3", false);

        String id = idOf(createFood(token, recipe("Arroz com feijão", rice.id(), "200", beans.id(), "100"))
                .andExpect(jsonPath("$.proteinG").value(3.27))
                .andExpect(jsonPath("$.baseUnit").value("G"))
                .andExpect(jsonPath("$.vegan").value(true))
                .andExpect(jsonPath("$.recipe.yieldGrams").value(300.0))
                .andExpect(jsonPath("$.recipe.ingredients.length()").value(2)));

        mvc.perform(put("/foods/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipe("Arroz com ovo", rice.id(), "200", egg.id(), "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vegan").value(false))
                .andExpect(jsonPath("$.proteinG").value(6.1));
    }

    @Test
    void ingredienteDeOutraNutricionistaInexistenteOuReceitaResponde400() throws Exception {
        String token = "nutri-" + UUID.randomUUID();
        Food rice = taco();
        Food theirs = ownOf(UUID.randomUUID());

        createFood(token, recipe("Receita", theirs.id(), "100", rice.id(), "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipe.ingredients[0].foodId"));
        createFood(token, recipe("Receita", rice.id(), "100", UUID.randomUUID(), "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipe.ingredients[1].foodId"));
        String mine = idOf(createFood(token, recipe("Base", rice.id(), "100", rice.id(), "100")));
        createFood(token, recipe("Receita de receita", UUID.fromString(mine), "100", rice.id(), "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipe.ingredients[0].foodId"));
    }

    @Test
    void bancoRecusaArquivoOuReceitaNaTacoEIngredienteForaDasRegras() {
        Food taco = taco();
        Food mine = ownOf(UUID.randomUUID());
        for (String[] update : new String[][] {
            {"update food set archived_at = now() where id = :id", "ck_food_archived"},
            {"update food set recipe_yield_g = 100 where id = :id", "ck_food_recipe"}
        }) {
            assertThatThrownBy(() -> jdbc.sql(update[0]).param("id", taco.id()).update())
                    .as(update[1])
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(update[1]);
        }
        String insert = """
                insert into food_recipe_ingredient (recipe_id, position, ingredient_id, amount)
                values (:recipe, 0, :ingredient, :amount)
                """;
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("recipe", mine.id())
                        .param("ingredient", taco.id())
                        .param("amount", new BigDecimal("5000.1"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_food_recipe_ingredient_amount");
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("recipe", mine.id())
                        .param("ingredient", mine.id())
                        .param("amount", new BigDecimal("10"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_food_recipe_ingredient_self");
    }

    @Test
    void receitaEmMlArquivarDeNovoEAlimentoQueJaEIngredienteNaoViraReceita() throws Exception {
        String token = "nutri-" + UUID.randomUUID();
        Food rice = taco();
        createFood(token, """
                        {"name": "Suco", "baseUnit": "ML", "recipe": {"yieldGrams": 300, "ingredients": [
                          {"foodId": "%s", "amount": 100}]}}
                        """.formatted(rice.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipe"));

        String granola = idOf(createFood(token, GRANOLA));
        mvc.perform(post("/foods/" + granola + "/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        Instant first = archivedAt(granola);
        mvc.perform(post("/foods/" + granola + "/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        assertThat(archivedAt(granola)).isEqualTo(first);

        // A granola entra numa receita; depois, ela mesma não pode virar receita.
        String base = idOf(createFood(token, GRANOLA));
        idOf(createFood(token, recipe("Mingau", UUID.fromString(base), "100", rice.id(), "100")));
        mvc.perform(put("/foods/" + base)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipe("Granola composta", rice.id(), "100", rice.id(), "100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipe"));
    }

    private Instant archivedAt(String foodId) {
        return jdbc.sql("select archived_at from food where id = :id")
                .param("id", UUID.fromString(foodId))
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    @Test
    void bancoRecusaPosicaoDeIngredienteERendimentoForaDaFaixa() {
        Food taco = taco();
        Food mine = ownOf(UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.sql("""
                                insert into food_recipe_ingredient (recipe_id, position, ingredient_id, amount)
                                values (:recipe, 30, :ingredient, 10)
                                """)
                        .param("recipe", mine.id())
                        .param("ingredient", taco.id())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_food_recipe_ingredient_position");
        for (String yield : new String[] {"0.5", "20000.1"}) {
            assertThatThrownBy(() -> jdbc.sql("update food set recipe_yield_g = :yield where id = :id")
                            .param("yield", new BigDecimal(yield))
                            .param("id", mine.id())
                            .update())
                    .as(yield)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_food_recipe");
        }
    }
}
