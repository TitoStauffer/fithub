package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Alimentos no Postgres do nutrition-service (migration V3). */
@Repository
class JdbcFoodRepository implements FoodRepository {

    private final JdbcClient jdbc;

    JdbcFoodRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Food food) {
        Food.Nutrients per100 = food.per100();
        jdbc.sql("""
                        insert into food (id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g,
                            fiber_g, vegan, external_source, external_id, created_at, updated_at)
                        values (:id, :kind, :ownerId, :name, :baseUnit, :energyKcal, :proteinG, :carbsG, :fatG,
                            :fiberG, :vegan, :externalSource, :externalId, :createdAt, :updatedAt)
                        on conflict (id) do update set
                            name = excluded.name,
                            base_unit = excluded.base_unit,
                            energy_kcal = excluded.energy_kcal,
                            protein_g = excluded.protein_g,
                            carbs_g = excluded.carbs_g,
                            fat_g = excluded.fat_g,
                            fiber_g = excluded.fiber_g,
                            vegan = excluded.vegan,
                            updated_at = excluded.updated_at
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
                .update();
    }

    @Override
    public Optional<Food> findById(UUID id) {
        return jdbc.sql("""
                        select id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g, fiber_g,
                            vegan, external_source, external_id, created_at, updated_at
                        from food where id = :id
                        """).param("id", id).query(JdbcFoodRepository::toFood).optional();
    }

    private static Food toFood(ResultSet row, int index) throws SQLException {
        String source = row.getString("external_source");
        return new Food(
                row.getObject("id", UUID.class),
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
                row.getTimestamp("updated_at").toInstant());
    }
}
