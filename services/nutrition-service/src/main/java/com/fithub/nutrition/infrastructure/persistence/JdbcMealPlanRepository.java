package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.plan.Meal;
import com.fithub.nutrition.domain.plan.MealItem;
import com.fithub.nutrition.domain.plan.MealPlan;
import com.fithub.nutrition.domain.plan.MealPlanRepository;
import com.fithub.nutrition.domain.plan.MealPlanStateException;
import com.fithub.nutrition.domain.plan.MealPlanVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Planos alimentares no Postgres do nutrition-service (migration V8). */
@Repository
class JdbcMealPlanRepository implements MealPlanRepository {

    private final JdbcClient jdbc;

    JdbcMealPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** O cabeçalho do plano e o número da versão atual (a maior). */
    private record Header(
            UUID id,
            UUID clientId,
            UUID nutritionistId,
            MealPlan.Status status,
            Instant createdAt,
            Instant updatedAt) {}

    @Override
    @Transactional
    public void add(MealPlan plan) {
        jdbc.sql("""
                        insert into meal_plan (id, client_id, nutritionist_id, status, created_at, updated_at)
                        values (:id, :clientId, :nutritionistId, :status, :createdAt, :updatedAt)
                        """)
                .param("id", plan.id())
                .param("clientId", plan.clientId())
                .param("nutritionistId", plan.nutritionistId())
                .param("status", plan.status().name())
                .param("createdAt", Timestamp.from(plan.createdAt()))
                .param("updatedAt", Timestamp.from(plan.updatedAt()))
                .update();
        MealPlanVersion version = plan.current();
        jdbc.sql("""
                        insert into meal_plan_version
                            (id, plan_id, number, name, starts_on, note, published_at, created_at)
                        values (:id, :planId, :number, :name, :startsOn, :note, :publishedAt, :createdAt)
                        """)
                .param("id", version.id())
                .param("planId", plan.id())
                .param("number", version.number())
                .param("name", version.name())
                .param("startsOn", version.startsOn())
                .param("note", version.note())
                .param("publishedAt", timestamp(version.publishedAt()))
                .param("createdAt", Timestamp.from(version.createdAt()))
                .update();
        for (Meal meal : version.meals()) {
            insertMeal(version.id(), meal);
        }
    }

    @Override
    @Transactional
    public void saveDraft(MealPlan plan, Instant readUpdatedAt) {
        // O updated_at lido é a guarda: só grava se o plano ainda é o rascunho lido. Publicar ou outra edição no meio
        // do caminho já trocou o updated_at, e o conteúdo publicado nunca é regravado (BR-09).
        int changed = jdbc.sql("""
                        update meal_plan set updated_at = :updatedAt
                        where id = :id and updated_at = :readUpdatedAt
                        """)
                .param("id", plan.id())
                .param("updatedAt", Timestamp.from(plan.updatedAt()))
                .param("readUpdatedAt", Timestamp.from(readUpdatedAt))
                .update();
        if (changed == 0) {
            throw changedMeanwhile();
        }
        MealPlanVersion version = plan.current();
        jdbc.sql("""
                        update meal_plan_version set name = :name, starts_on = :startsOn, note = :note
                        where id = :id
                        """)
                .param("id", version.id())
                .param("name", version.name())
                .param("startsOn", version.startsOn())
                .param("note", version.note())
                .update();
        // As refeições do rascunho são regravadas inteiras; os itens vão junto pelo cascade.
        jdbc.sql("delete from meal where version_id = :versionId")
                .param("versionId", version.id())
                .update();
        for (Meal meal : version.meals()) {
            insertMeal(version.id(), meal);
        }
    }

    @Override
    @Transactional
    public void saveStatus(MealPlan plan, Instant readUpdatedAt) {
        // Publicar e substituir só trocam o estado e a data de publicação: as refeições ficam como estão.
        int changed = jdbc.sql("""
                        update meal_plan set status = :status, updated_at = :updatedAt
                        where id = :id and updated_at = :readUpdatedAt
                        """)
                .param("id", plan.id())
                .param("status", plan.status().name())
                .param("updatedAt", Timestamp.from(plan.updatedAt()))
                .param("readUpdatedAt", Timestamp.from(readUpdatedAt))
                .update();
        if (changed == 0) {
            throw changedMeanwhile();
        }
        jdbc.sql("update meal_plan_version set published_at = :publishedAt where id = :id")
                .param("id", plan.current().id())
                .param("publishedAt", timestamp(plan.current().publishedAt()))
                .update();
    }

    private static MealPlanStateException changedMeanwhile() {
        return new MealPlanStateException("O plano mudou desde que foi aberto. Recarregue e tente de novo.");
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private void insertMeal(UUID versionId, Meal meal) {
        jdbc.sql("""
                        insert into meal (version_id, position, name, suggested_time, note)
                        values (:versionId, :position, :name, :suggestedTime, :note)
                        """)
                .param("versionId", versionId)
                .param("position", meal.position())
                .param("name", meal.name())
                .param("suggestedTime", meal.suggestedTime() == null ? null : Time.valueOf(meal.suggestedTime()))
                .param("note", meal.note())
                .update();
        for (int position = 0; position < meal.items().size(); position++) {
            MealItem item = meal.items().get(position);
            jdbc.sql("""
                            insert into meal_item
                                (version_id, meal_position, position, food_id, amount, measure_id, measure_count)
                            values (:versionId, :mealPosition, :position, :foodId, :amount, :measureId,
                                :measureCount)
                            """)
                    .param("versionId", versionId)
                    .param("mealPosition", meal.position())
                    .param("position", position)
                    .param("foodId", item.foodId())
                    .param("amount", item.amount())
                    .param("measureId", item.measureId())
                    .param("measureCount", item.measureCount())
                    .update();
        }
    }

    @Override
    public Optional<MealPlan> findById(UUID id) {
        return jdbc.sql("""
                        select id, client_id, nutritionist_id, status, created_at, updated_at
                        from meal_plan where id = :id
                        """)
                .param("id", id)
                .query(JdbcMealPlanRepository::toHeader)
                .optional()
                .map(this::withCurrentVersion);
    }

    @Override
    public List<MealPlan> findByClientAndNutritionist(UUID clientId, UUID nutritionistId) {
        return jdbc
                .sql("""
                        select id, client_id, nutritionist_id, status, created_at, updated_at
                        from meal_plan
                        where client_id = :clientId and nutritionist_id = :nutritionistId
                        order by created_at desc, id desc
                        """)
                .param("clientId", clientId)
                .param("nutritionistId", nutritionistId)
                .query(JdbcMealPlanRepository::toHeader)
                .list()
                .stream()
                .map(this::withCurrentVersion)
                .toList();
    }

    @Override
    public Optional<MealPlan> findPublished(UUID clientId) {
        return jdbc.sql("""
                        select id, client_id, nutritionist_id, status, created_at, updated_at
                        from meal_plan where client_id = :clientId and status = 'PUBLISHED'
                        """)
                .param("clientId", clientId)
                .query(JdbcMealPlanRepository::toHeader)
                .optional()
                .map(this::withCurrentVersion);
    }

    private MealPlan withCurrentVersion(Header header) {
        record VersionRow(
                UUID id,
                int number,
                String name,
                LocalDate startsOn,
                String note,
                Instant publishedAt,
                Instant createdAt) {}
        VersionRow row = jdbc.sql("""
                        select id, number, name, starts_on, note, published_at, created_at
                        from meal_plan_version where plan_id = :planId
                        order by number desc limit 1
                        """)
                .param("planId", header.id())
                .query((rs, index) -> new VersionRow(
                        rs.getObject("id", UUID.class),
                        rs.getInt("number"),
                        rs.getString("name"),
                        rs.getObject("starts_on", LocalDate.class),
                        rs.getString("note"),
                        instant(rs.getTimestamp("published_at")),
                        rs.getTimestamp("created_at").toInstant()))
                .single();
        MealPlanVersion version = new MealPlanVersion(
                row.id(),
                row.number(),
                row.name(),
                row.startsOn(),
                row.note(),
                meals(row.id()),
                row.publishedAt(),
                row.createdAt());
        return new MealPlan(
                header.id(),
                header.clientId(),
                header.nutritionistId(),
                header.status(),
                version,
                header.createdAt(),
                header.updatedAt());
    }

    private List<Meal> meals(UUID versionId) {
        Map<Integer, List<MealItem>> items = new LinkedHashMap<>();
        jdbc.sql("""
                        select meal_position, food_id, amount, measure_id, measure_count
                        from meal_item where version_id = :versionId
                        order by meal_position, position
                        """).param("versionId", versionId).query(rs -> {
            UUID measureId = rs.getObject("measure_id", UUID.class);
            // A medida apagada pela nutricionista vira vazia (on delete set null); a contagem perde o sentido.
            items.computeIfAbsent(rs.getInt("meal_position"), position -> new ArrayList<>())
                    .add(new MealItem(
                            rs.getObject("food_id", UUID.class),
                            rs.getBigDecimal("amount"),
                            measureId,
                            measureId == null ? null : rs.getBigDecimal("measure_count")));
        });
        return jdbc.sql("""
                        select position, name, suggested_time, note
                        from meal where version_id = :versionId
                        order by position
                        """)
                .param("versionId", versionId)
                .query((rs, index) -> {
                    int position = rs.getInt("position");
                    return new Meal(
                            position,
                            rs.getString("name"),
                            rs.getObject("suggested_time", LocalTime.class),
                            rs.getString("note"),
                            items.getOrDefault(position, List.of()));
                })
                .list();
    }

    private static Header toHeader(ResultSet row, int index) throws SQLException {
        return new Header(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("nutritionist_id", UUID.class),
                MealPlan.Status.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
