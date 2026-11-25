package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.log.MealLog;
import com.fithub.nutrition.domain.log.MealLogRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Registros de refeição no Postgres do nutrition-service (migration V10). */
@Repository
class JdbcMealLogRepository implements MealLogRepository {

    private static final String SELECT = """
            select id, client_id, plan_id, version_id, version_number, meal_position, logged_on, status, deviation,
                photo_key, photo_uploaded_at, created_at, updated_at
            from meal_log
            """;

    private final JdbcClient jdbc;

    JdbcMealLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void add(MealLog log) {
        jdbc.sql("""
                        insert into meal_log (id, client_id, plan_id, version_id, version_number, meal_position,
                            logged_on, status, deviation, created_at, updated_at)
                        values (:id, :clientId, :planId, :versionId, :versionNumber, :mealPosition, :loggedOn,
                            :status, :deviation, :createdAt, :updatedAt)
                        """)
                .param("id", log.id())
                .param("clientId", log.clientId())
                .param("planId", log.planId())
                .param("versionId", log.versionId())
                .param("versionNumber", log.versionNumber())
                .param("mealPosition", log.mealPosition())
                .param("loggedOn", log.loggedOn())
                .param("status", log.status().name())
                .param("deviation", log.deviation())
                .param("createdAt", Timestamp.from(log.createdAt()))
                .param("updatedAt", Timestamp.from(log.updatedAt()))
                .update();
        insertSubstitutions(log);
    }

    @Override
    @Transactional
    public void update(MealLog log) {
        jdbc.sql("update meal_log set status = :status, deviation = :deviation, updated_at = :updatedAt where id = :id")
                .param("id", log.id())
                .param("status", log.status().name())
                .param("deviation", log.deviation())
                .param("updatedAt", Timestamp.from(log.updatedAt()))
                .update();
        jdbc.sql("delete from meal_log_substitution where log_id = :id")
                .param("id", log.id())
                .update();
        insertSubstitutions(log);
    }

    @Override
    public void savePhoto(MealLog log) {
        jdbc.sql("""
                        update meal_log set photo_key = :photoKey, photo_uploaded_at = :photoUploadedAt,
                            updated_at = :updatedAt
                        where id = :id
                        """)
                .param("id", log.id())
                .param("photoKey", log.photo() == null ? null : log.photo().objectKey())
                .param(
                        "photoUploadedAt",
                        log.photo() == null ? null : Timestamp.from(log.photo().uploadedAt()))
                .param("updatedAt", Timestamp.from(log.updatedAt()))
                .update();
    }

    @Override
    public boolean existsByPhotoKey(String objectKey) {
        return jdbc.sql("select count(*) from meal_log where photo_key = :key")
                        .param("key", objectKey)
                        .query(Long.class)
                        .single()
                > 0;
    }

    private void insertSubstitutions(MealLog log) {
        for (MealLog.Substitution substitution : log.substitutions()) {
            jdbc.sql("""
                            insert into meal_log_substitution (log_id, item_position, alternative)
                            values (:logId, :itemPosition, :alternative)
                            """)
                    .param("logId", log.id())
                    .param("itemPosition", substitution.itemPosition())
                    .param("alternative", substitution.alternative())
                    .update();
        }
    }

    @Override
    public Optional<MealLog> findById(UUID id) {
        return withSubstitutions(jdbc.sql(SELECT + "where id = :id")
                        .param("id", id)
                        .query(JdbcMealLogRepository::toLog)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public List<MealLog> findByPlanAndDay(UUID clientId, UUID planId, LocalDate day) {
        return withSubstitutions(jdbc.sql(
                        SELECT
                                + "where client_id = :clientId and plan_id = :planId and logged_on = :day order by meal_position")
                .param("clientId", clientId)
                .param("planId", planId)
                .param("day", day)
                .query(JdbcMealLogRepository::toLog)
                .list());
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("delete from meal_log where id = :id").param("id", id).update();
    }

    /** Os registros com as alternativas usadas, numa consulta só. */
    private List<MealLog> withSubstitutions(List<MealLog> logs) {
        if (logs.isEmpty()) {
            return logs;
        }
        Map<UUID, List<MealLog.Substitution>> byLog = new HashMap<>();
        jdbc.sql("""
                        select log_id, item_position, alternative from meal_log_substitution
                        where log_id in (:ids) order by item_position
                        """).param("ids", logs.stream().map(MealLog::id).toList()).query(rs -> {
            byLog.computeIfAbsent(rs.getObject("log_id", UUID.class), id -> new ArrayList<>())
                    .add(new MealLog.Substitution(rs.getInt("item_position"), rs.getInt("alternative")));
        });
        return logs.stream()
                .map(log -> new MealLog(
                        log.id(),
                        log.clientId(),
                        log.planId(),
                        log.versionId(),
                        log.versionNumber(),
                        log.mealPosition(),
                        log.loggedOn(),
                        log.status(),
                        byLog.getOrDefault(log.id(), List.of()),
                        log.deviation(),
                        log.photo(),
                        log.createdAt(),
                        log.updatedAt()))
                .toList();
    }

    private static MealLog.Photo photoOf(ResultSet row) throws SQLException {
        String key = row.getString("photo_key");
        return key == null
                ? null
                : new MealLog.Photo(key, row.getTimestamp("photo_uploaded_at").toInstant());
    }

    private static MealLog toLog(ResultSet row, int index) throws SQLException {
        return new MealLog(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("plan_id", UUID.class),
                row.getObject("version_id", UUID.class),
                row.getInt("version_number"),
                row.getInt("meal_position"),
                row.getObject("logged_on", LocalDate.class),
                MealLog.Status.valueOf(row.getString("status")),
                List.of(),
                row.getString("deviation"),
                photoOf(row),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
