package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

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
}
