package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.progress.ProgressRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de evolução do treino no Postgres do training-service (DEV-0091), sem tabela derivada. */
@Repository
class JdbcProgressRepository implements ProgressRepository {

    /** Filtro das fichas do personal; com o parâmetro vazio, vale toda ficha do aluno. */
    private static final String PERSONAL_FILTER =
            " and (cast(:personalId as uuid) is null or p.personal_id = cast(:personalId as uuid))";

    private final JdbcClient jdbc;

    JdbcProgressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlanActivity> publishedPlans(UUID clientId, UUID personalId) {
        return jdbc
                .sql("""
                        select p.id, p.status, p.updated_at,
                            (select min(v.published_at) from workout_plan_version v where v.plan_id = p.id) first_published,
                            (select count(*) from workout_division d
                              where d.version_id = (select v2.id from workout_plan_version v2 where v2.plan_id = p.id
                                                     order by v2.number desc limit 1)) divisions
                        from workout_plan p
                        where p.client_id = :clientId and p.status <> 'DRAFT'
                        """ + PERSONAL_FILTER)
                .param("clientId", clientId)
                .param("personalId", personalId)
                .query((rs, index) -> {
                    Timestamp published = rs.getTimestamp("first_published");
                    if (published == null) {
                        return null;
                    }
                    Instant replacedAt = "REPLACED".equals(rs.getString("status"))
                            ? rs.getTimestamp("updated_at").toInstant()
                            : null;
                    return new PlanActivity(
                            rs.getObject("id", UUID.class), published.toInstant(), replacedAt, rs.getInt("divisions"));
                })
                .list()
                .stream()
                .filter(plan -> plan != null)
                .toList();
    }

    @Override
    public List<Instant> finishedSessions(UUID clientId, UUID personalId, Instant from, Instant to) {
        return jdbc.sql("""
                        select s.finished_at from workout_session s join workout_plan p on p.id = s.plan_id
                        where s.client_id = :clientId and s.finished_at >= :from and s.finished_at < :to
                        """ + PERSONAL_FILTER + " order by s.finished_at")
                .param("clientId", clientId)
                .param("personalId", personalId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, index) -> rs.getTimestamp("finished_at").toInstant())
                .list();
    }

    @Override
    public List<ExerciseSession> exerciseSessions(UUID clientId, UUID personalId, Instant from, Instant to) {
        return jdbc.sql("""
                        select s.id session_id, s.finished_at, w.exercise_id,
                            max(case when w.reps >= 1 then w.load_kg end) max_load,
                            sum(w.load_kg * w.reps) volume
                        from workout_set w
                            join workout_session s on s.id = w.session_id
                            join workout_plan p on p.id = s.plan_id
                        where s.client_id = :clientId and s.finished_at >= :from and s.finished_at < :to
                        """ + PERSONAL_FILTER + """

                        group by s.id, s.finished_at, w.exercise_id
                        order by s.finished_at, w.exercise_id
                        """)
                .param("clientId", clientId)
                .param("personalId", personalId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, index) -> new ExerciseSession(
                        rs.getObject("session_id", UUID.class),
                        rs.getTimestamp("finished_at").toInstant(),
                        rs.getObject("exercise_id", UUID.class),
                        rs.getBigDecimal("max_load"),
                        rs.getBigDecimal("volume")))
                .list();
    }

    @Override
    public Map<UUID, Instant> lastFinished(UUID personalId, Collection<UUID> clientIds) {
        Map<UUID, Instant> last = new HashMap<>();
        if (clientIds.isEmpty()) {
            return last;
        }
        jdbc.sql("""
                        select s.client_id, max(s.finished_at) last_finished
                        from workout_session s join workout_plan p on p.id = s.plan_id
                        where p.personal_id = :personalId and s.client_id in (:clientIds) and s.finished_at is not null
                        group by s.client_id
                        """)
                .param("personalId", personalId)
                .param("clientIds", clientIds)
                .query(rs -> {
                    last.put(
                            rs.getObject("client_id", UUID.class),
                            rs.getTimestamp("last_finished").toInstant());
                });
        return last;
    }
}
