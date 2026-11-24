package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.food.FoodMeasure;
import com.fithub.nutrition.domain.food.FoodMeasureRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Medidas caseiras no Postgres do nutrition-service (migration V4). */
@Repository
class JdbcFoodMeasureRepository implements FoodMeasureRepository {

    private static final String SELECT = "select id, food_id, owner_id, name, amount, created_at from food_measure ";

    private final JdbcClient jdbc;

    JdbcFoodMeasureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FoodMeasure measure) {
        jdbc.sql("""
                        insert into food_measure (id, food_id, owner_id, name, amount, created_at)
                        values (:id, :foodId, :ownerId, :name, :amount, :createdAt)
                        """)
                .param("id", measure.id())
                .param("foodId", measure.foodId())
                .param("ownerId", measure.ownerId())
                .param("name", measure.name())
                .param("amount", measure.amount())
                .param("createdAt", Timestamp.from(measure.createdAt()))
                .update();
    }

    @Override
    public Optional<FoodMeasure> findById(UUID id) {
        return jdbc.sql(SELECT + "where id = :id")
                .param("id", id)
                .query(JdbcFoodMeasureRepository::toMeasure)
                .optional();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("delete from food_measure where id = :id").param("id", id).update();
    }

    @Override
    public List<FoodMeasure> findFor(UUID foodId, UUID ownerId) {
        return jdbc.sql(SELECT + "where food_id = :foodId and owner_id = :ownerId order by lower(name), id")
                .param("foodId", foodId)
                .param("ownerId", ownerId)
                .query(JdbcFoodMeasureRepository::toMeasure)
                .list();
    }

    private static FoodMeasure toMeasure(ResultSet row, int index) throws SQLException {
        return new FoodMeasure(
                row.getObject("id", UUID.class),
                row.getObject("food_id", UUID.class),
                row.getObject("owner_id", UUID.class),
                row.getString("name"),
                row.getBigDecimal("amount"),
                row.getTimestamp("created_at").toInstant());
    }
}
