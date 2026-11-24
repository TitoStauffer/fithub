package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.food.Recipe;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Alimentos no Postgres do nutrition-service (migrations V3 e V5), com os ingredientes da receita. */
@Repository
class JdbcFoodRepository implements FoodRepository {

    private static final String SELECT = """
            select id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g, fiber_g, vegan,
                external_source, external_id, created_at, updated_at, recipe_yield_g, archived_at
            from food
            """;

    private final JdbcClient jdbc;

    JdbcFoodRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** O alimento e, na receita, os ingredientes, numa transação só. */
    @Override
    @Transactional
    public void save(Food food) {
        Food.Nutrients per100 = food.per100();
        jdbc.sql("""
                        insert into food (id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g,
                            fiber_g, vegan, external_source, external_id, created_at, updated_at, recipe_yield_g,
                            archived_at)
                        values (:id, :kind, :ownerId, :name, :baseUnit, :energyKcal, :proteinG, :carbsG, :fatG,
                            :fiberG, :vegan, :externalSource, :externalId, :createdAt, :updatedAt, :recipeYield,
                            :archivedAt)
                        on conflict (id) do update set
                            name = excluded.name,
                            base_unit = excluded.base_unit,
                            energy_kcal = excluded.energy_kcal,
                            protein_g = excluded.protein_g,
                            carbs_g = excluded.carbs_g,
                            fat_g = excluded.fat_g,
                            fiber_g = excluded.fiber_g,
                            vegan = excluded.vegan,
                            updated_at = excluded.updated_at,
                            recipe_yield_g = excluded.recipe_yield_g,
                            archived_at = excluded.archived_at
                        """)
                .param("id", food.id())
                .param("kind", food.kind().name())
                .param("ownerId", food.ownerId())
                .param("name", food.name())
                .param("baseUnit", food.baseUnit().name())
                .param("energyKcal", per100.energyKcal())
                .param("proteinG", per100.proteinG())
                .param("carbsG", per100.carbsG())
                .param("fatG", per100.fatG())
                .param("fiberG", per100.fiberG())
                .param("vegan", food.vegan())
                .param(
                        "externalSource",
                        food.source() == null ? null : food.source().name())
                .param(
                        "externalId",
                        food.source() == null ? null : food.source().externalId())
                .param("createdAt", Timestamp.from(food.createdAt()))
                .param("updatedAt", Timestamp.from(food.updatedAt()))
                .param("recipeYield", food.isRecipe() ? food.recipe().yieldGrams() : null)
                .param("archivedAt", food.archivedAt() == null ? null : Timestamp.from(food.archivedAt()))
                .update();
        jdbc.sql("delete from food_recipe_ingredient where recipe_id = :id")
                .param("id", food.id())
                .update();
        if (food.isRecipe()) {
            List<Recipe.Ingredient> ingredients = food.recipe().ingredients();
            for (int position = 0; position < ingredients.size(); position++) {
                jdbc.sql("""
                                insert into food_recipe_ingredient (recipe_id, position, ingredient_id, amount)
                                values (:recipeId, :position, :ingredientId, :amount)
                                """)
                        .param("recipeId", food.id())
                        .param("position", position)
                        .param("ingredientId", ingredients.get(position).foodId())
                        .param("amount", ingredients.get(position).amount())
                        .update();
            }
        }
    }

    @Override
    public Optional<Food> findById(UUID id) {
        return jdbc.sql(SELECT + "where id = :id")
                .param("id", id)
                .query(this::toFood)
                .optional();
    }

    @Override
    public Optional<Food> findBySource(String source, String externalId) {
        return jdbc.sql(SELECT + "where external_source = :source and external_id = :externalId")
                .param("source", source)
                .param("externalId", externalId)
                .query(this::toFood)
                .optional();
    }

    private Food toFood(ResultSet row, int index) throws SQLException {
        String source = row.getString("external_source");
        UUID id = row.getObject("id", UUID.class);
        BigDecimal recipeYield = row.getBigDecimal("recipe_yield_g");
        Timestamp archivedAt = row.getTimestamp("archived_at");
        return new Food(
                id,
                Food.Kind.valueOf(row.getString("kind")),
                row.getObject("owner_id", UUID.class),
                row.getString("name"),
                Food.BaseUnit.valueOf(row.getString("base_unit")),
                new Food.Nutrients(
                        row.getBigDecimal("energy_kcal"),
                        row.getBigDecimal("protein_g"),
                        row.getBigDecimal("carbs_g"),
                        row.getBigDecimal("fat_g"),
                        row.getBigDecimal("fiber_g")),
                row.getBoolean("vegan"),
                source == null ? null : new Food.Source(source, row.getString("external_id")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                recipeYield == null ? null : new Recipe(ingredientsOf(id), recipeYield),
                archivedAt == null ? null : archivedAt.toInstant());
    }

    private List<Recipe.Ingredient> ingredientsOf(UUID recipeId) {
        return jdbc.sql("""
                        select ingredient_id, amount from food_recipe_ingredient
                        where recipe_id = :recipeId order by position
                        """)
                .param("recipeId", recipeId)
                .query((row, index) ->
                        new Recipe.Ingredient(row.getObject("ingredient_id", UUID.class), row.getBigDecimal("amount")))
                .list();
    }
}
