package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.session.SessionRepository;
import com.fithub.training.domain.session.WorkoutSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Sessões de treino no Postgres do training-service (migration V10). */
@Repository
class JdbcSessionRepository implements SessionRepository {

    private static final String SELECT = """
            select id, client_id, plan_id, version_id, division_position, started_at, finished_at, comment, feeling
            from workout_session
            """;

    private final JdbcClient jdbc;

    JdbcSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(WorkoutSession session) {
        // Iniciar grava a sessão; finalizar só preenche finished_at e nunca desfaz uma finalização. O comentário tem
        // gravação própria (saveFeedback): assim, finalizar e comentar ao mesmo tempo não apagam um ao outro.
        jdbc.sql("""
                        insert into workout_session (id, client_id, plan_id, version_id, division_position, started_at,
                            finished_at, comment, feeling)
                        values (:id, :clientId, :planId, :versionId, :division, :startedAt, :finishedAt, :comment,
                            :feeling)
                        on conflict (id) do update set
                            finished_at = coalesce(workout_session.finished_at, excluded.finished_at)
                        """)
                .param("id", session.id())
                .param("clientId", session.clientId())
                .param("planId", session.planId())
                .param("versionId", session.versionId())
                .param("division", session.divisionPosition())
                .param("startedAt", Timestamp.from(session.startedAt()))
                .param("finishedAt", session.finishedAt() == null ? null : Timestamp.from(session.finishedAt()))
                .param("comment", session.comment())
                .param("feeling", session.feeling())
                .update();
    }

    @Override
    public void saveFeedback(WorkoutSession session) {
        jdbc.sql("update workout_session set comment = :comment, feeling = :feeling where id = :id")
                .param("id", session.id())
                .param("comment", session.comment())
                .param("feeling", session.feeling())
                .update();
    }

    @Override
    public Optional<WorkoutSession> findById(UUID id) {
        return jdbc.sql(SELECT + "where id = :id")
                .param("id", id)
                .query(JdbcSessionRepository::toSession)
                .optional();
    }

    @Override
    public Optional<WorkoutSession> lastFinished(UUID planId) {
        return jdbc.sql(SELECT
                        + "where plan_id = :planId and finished_at is not null order by finished_at desc limit 1")
                .param("planId", planId)
                .query(JdbcSessionRepository::toSession)
                .optional();
    }

    @Override
    public Optional<WorkoutSession> open(UUID planId) {
        return jdbc.sql(SELECT + "where plan_id = :planId and finished_at is null")
                .param("planId", planId)
                .query(JdbcSessionRepository::toSession)
                .optional();
    }

    private static WorkoutSession toSession(ResultSet row, int index) throws SQLException {
        Timestamp finishedAt = row.getTimestamp("finished_at");
        return new WorkoutSession(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("plan_id", UUID.class),
                row.getObject("version_id", UUID.class),
                row.getInt("division_position"),
                row.getTimestamp("started_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                row.getString("comment"),
                row.getObject("feeling", Integer.class));
    }
}
