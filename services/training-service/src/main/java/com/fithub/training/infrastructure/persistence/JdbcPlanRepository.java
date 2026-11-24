package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
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

    private WorkoutPlan toPlan(ResultSet row, int index) throws SQLException {
        UUID versionId = row.getObject("version_id", UUID.class);
        List<PlanVersion.Division> divisions = jdbc.sql(
                        "select letter, name from workout_division where version_id = :id order by position")
                .param("id", versionId)
                .query((division, i) ->
                        new PlanVersion.Division(division.getString("letter"), division.getString("name")))
                .list();
        Timestamp publishedAt = row.getTimestamp("published_at");
        return new WorkoutPlan(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("personal_id", UUID.class),
                WorkoutPlan.Status.valueOf(row.getString("status")),
                new PlanVersion(
                        versionId,
                        row.getInt("number"),
                        row.getString("name"),
                        row.getObject("starts_on", LocalDate.class),
                        row.getInt("validity_weeks"),
                        divisions,
                        publishedAt == null ? null : publishedAt.toInstant(),
                        row.getTimestamp("version_created_at").toInstant()),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
