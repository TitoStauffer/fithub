package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
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

/** Fichas de treino no Postgres do training-service (migration V8). A versão atual é a de maior número. */
@Repository
class JdbcPlanRepository implements PlanRepository {

    private static final String SELECT = """
            select p.id, p.client_id, p.personal_id, p.status, p.created_at, p.updated_at,
                v.id as version_id, v.number, v.name, v.starts_on, v.validity_weeks, v.published_at,
                v.created_at as version_created_at
            from workout_plan p
            join workout_plan_version v on v.plan_id = p.id
                and v.number = (select max(number) from workout_plan_version where plan_id = p.id)
            """;

    private final JdbcClient jdbc;

    JdbcPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cabeçalho, versão atual e divisões numa transação só. */
    @Override
    @Transactional
    public void save(WorkoutPlan plan) {
        jdbc.sql("""
                        insert into workout_plan (id, client_id, personal_id, status, created_at, updated_at)
                        values (:id, :clientId, :personalId, :status, :createdAt, :updatedAt)
                        on conflict (id) do update set status = excluded.status, updated_at = excluded.updated_at
                        """)
                .param("id", plan.id())
                .param("clientId", plan.clientId())
                .param("personalId", plan.personalId())
                .param("status", plan.status().name())
                .param("createdAt", Timestamp.from(plan.createdAt()))
                .param("updatedAt", Timestamp.from(plan.updatedAt()))
                .update();
        PlanVersion version = plan.current();
        jdbc.sql("""
                        insert into workout_plan_version (id, plan_id, number, name, starts_on, validity_weeks,
                            published_at, created_at)
                        values (:id, :planId, :number, :name, :startsOn, :validityWeeks, :publishedAt, :createdAt)
                        on conflict (id) do update set
                            name = excluded.name,
                            starts_on = excluded.starts_on,
                            validity_weeks = excluded.validity_weeks,
                            published_at = excluded.published_at
                        """)
                .param("id", version.id())
                .param("planId", plan.id())
                .param("number", version.number())
                .param("name", version.name())
                .param("startsOn", version.startsOn())
                .param("validityWeeks", version.validityWeeks())
                .param("publishedAt", version.publishedAt() == null ? null : Timestamp.from(version.publishedAt()))
                .param("createdAt", Timestamp.from(version.createdAt()))
                .update();
        jdbc.sql("delete from workout_division where version_id = :id")
                .param("id", version.id())
                .update();
        for (int position = 0; position < version.divisions().size(); position++) {
            PlanVersion.Division division = version.divisions().get(position);
            jdbc.sql("""
                            insert into workout_division (version_id, position, letter, name)
                            values (:versionId, :position, :letter, :name)
                            """)
                    .param("versionId", version.id())
                    .param("position", position)
                    .param("letter", division.letter())
                    .param("name", division.name())
                    .update();
            saveItems(version.id(), position, division.items());
        }
    }

    /** Itens da divisão e as alternativas de cada um, na ordem (DEV-0060). */
    private void saveItems(UUID versionId, int divisionPosition, List<PlanItem> items) {
        for (int position = 0; position < items.size(); position++) {
            PlanItem item = items.get(position);
            jdbc.sql("""
                            insert into workout_item (version_id, division_position, position, exercise_id, sets,
                                reps_min, reps_max, load_kg, rest_seconds, note, technique)
                            values (:versionId, :division, :position, :exerciseId, :sets, :repsMin, :repsMax, :loadKg,
                                :restSeconds, :note, :technique)
                            """)
                    .param("versionId", versionId)
                    .param("division", divisionPosition)
                    .param("position", position)
                    .param("exerciseId", item.exerciseId())
                    .param("sets", item.sets())
                    .param("repsMin", item.repsMin())
                    .param("repsMax", item.repsMax())
                    .param("loadKg", item.loadKg())
                    .param("restSeconds", item.restSeconds())
                    .param("note", item.note())
                    .param(
                            "technique",
                            item.technique() == null ? null : item.technique().name())
                    .update();
            for (int alternative = 0; alternative < item.alternatives().size(); alternative++) {
                jdbc.sql("""
                                insert into workout_item_alternative (version_id, division_position, item_position,
                                    position, exercise_id)
                                values (:versionId, :division, :item, :position, :exerciseId)
                                """)
                        .param("versionId", versionId)
                        .param("division", divisionPosition)
                        .param("item", position)
                        .param("position", alternative)
                        .param("exerciseId", item.alternatives().get(alternative))
                        .update();
            }
        }
    }

    @Override
    public Optional<WorkoutPlan> findById(UUID id) {
        return jdbc.sql(SELECT + "where p.id = :id")
                .param("id", id)
                .query(this::toPlan)
                .optional();
    }

    @Override
    public List<WorkoutPlan> findByClientAndPersonal(UUID clientId, UUID personalId) {
        return jdbc.sql(SELECT + "where p.client_id = :clientId and p.personal_id = :personalId"
                        + " order by p.created_at desc, p.id")
                .param("clientId", clientId)
                .param("personalId", personalId)
                .query(this::toPlan)
                .list();
    }

    @Override
    public Optional<WorkoutPlan> findPublished(UUID clientId, UUID personalId) {
        return jdbc.sql(SELECT + "where p.client_id = :clientId and p.personal_id = :personalId"
                        + " and p.status = 'PUBLISHED'")
                .param("clientId", clientId)
                .param("personalId", personalId)
                .query(this::toPlan)
                .optional();
    }

    @Override
    public List<ExpiringPlan> expiring(UUID personalId, LocalDate until) {
        return jdbc.sql("""
                        select p.id, p.client_id, c.name as client_name, v.name,
                            v.starts_on + (v.validity_weeks * 7) as expires_on
                        from workout_plan p
                        join workout_plan_version v on v.plan_id = p.id
                            and v.number = (select max(number) from workout_plan_version where plan_id = p.id)
                        left join client c on c.client_id = p.client_id
                        where p.personal_id = :personalId
                            and p.status = 'PUBLISHED'
                            and v.starts_on + (v.validity_weeks * 7) <= :until
                            and exists (select 1 from client_link l
                                        where l.professional_id = p.personal_id and l.client_id = p.client_id
                                            and l.status = 'ACTIVE')
                        order by expires_on, p.id
                        """)
                .param("personalId", personalId)
                .param("until", until)
                .query((row, index) -> new ExpiringPlan(
                        row.getObject("id", UUID.class),
                        row.getObject("client_id", UUID.class),
                        row.getString("client_name"),
                        row.getString("name"),
                        row.getObject("expires_on", LocalDate.class)))
                .list();
    }

    /** Itens da versão por posição da divisão, já com as alternativas, na ordem. */
    private Map<Integer, List<PlanItem>> items(UUID versionId) {
        Map<String, List<UUID>> alternatives = new HashMap<>();
        jdbc.sql("""
                        select division_position, item_position, exercise_id from workout_item_alternative
                        where version_id = :id order by division_position, item_position, position
                        """).param("id", versionId).query(row -> {
            alternatives
                    .computeIfAbsent(
                            row.getInt("division_position") + ":" + row.getInt("item_position"),
                            key -> new ArrayList<>())
                    .add(row.getObject("exercise_id", UUID.class));
        });
        Map<Integer, List<PlanItem>> items = new HashMap<>();
        jdbc.sql("""
                        select division_position, position, exercise_id, sets, reps_min, reps_max, load_kg,
                            rest_seconds, note, technique
                        from workout_item where version_id = :id order by division_position, position
                        """).param("id", versionId).query(row -> {
            String technique = row.getString("technique");
            int division = row.getInt("division_position");
            items.computeIfAbsent(division, key -> new ArrayList<>())
                    .add(new PlanItem(
                            row.getObject("exercise_id", UUID.class),
                            row.getInt("sets"),
                            row.getInt("reps_min"),
                            row.getInt("reps_max"),
                            row.getBigDecimal("load_kg"),
                            row.getObject("rest_seconds", Integer.class),
                            row.getString("note"),
                            technique == null ? null : PlanItem.Technique.valueOf(technique),
                            alternatives.getOrDefault(division + ":" + row.getInt("position"), List.of())));
        });
        return items;
    }

    private WorkoutPlan toPlan(ResultSet row, int index) throws SQLException {
        return new WorkoutPlan(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("personal_id", UUID.class),
                WorkoutPlan.Status.valueOf(row.getString("status")),
                toVersion(row),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    /** A versão da linha (colunas `version_id`, `number`, `name`...), com as divisões e os itens. */
    private PlanVersion toVersion(ResultSet row) throws SQLException {
        UUID versionId = row.getObject("version_id", UUID.class);
        Map<Integer, List<PlanItem>> items = items(versionId);
        List<PlanVersion.Division> divisions = jdbc.sql(
                        "select position, letter, name from workout_division where version_id = :id order by position")
                .param("id", versionId)
                .query((division, i) -> new PlanVersion.Division(
                        division.getString("letter"),
                        division.getString("name"),
                        items.getOrDefault(division.getInt("position"), List.of())))
                .list();
        Timestamp publishedAt = row.getTimestamp("published_at");
        return new PlanVersion(
                versionId,
                row.getInt("number"),
                row.getString("name"),
                row.getObject("starts_on", LocalDate.class),
                row.getInt("validity_weeks"),
                divisions,
                publishedAt == null ? null : publishedAt.toInstant(),
                row.getTimestamp("version_created_at").toInstant());
    }

    @Override
    public List<VersionSummary> findVersions(UUID planId) {
        return jdbc.sql("""
                        select number, name, published_at from workout_plan_version
                        where plan_id = :planId order by number
                        """)
                .param("planId", planId)
                .query((row, index) -> {
                    Timestamp publishedAt = row.getTimestamp("published_at");
                    return new VersionSummary(
                            row.getInt("number"),
                            row.getString("name"),
                            publishedAt == null ? null : publishedAt.toInstant());
                })
                .list();
    }

    @Override
    public Optional<PlanVersion> findVersion(UUID planId, int number) {
        return jdbc.sql("""
                        select id as version_id, number, name, starts_on, validity_weeks, published_at,
                            created_at as version_created_at
                        from workout_plan_version where plan_id = :planId and number = :number
                        """)
                .param("planId", planId)
                .param("number", number)
                .query((row, index) -> toVersion(row))
                .optional();
    }
}
