package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.session.SetRepository;
import com.fithub.training.domain.session.WorkoutSet;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Séries registradas no Postgres do training-service (migration V11). */
@Repository
class JdbcSetRepository implements SetRepository {

    private static final String SELECT = """
            select s.id, s.session_id, s.item_position, s.set_number, s.exercise_id, s.load_kg, s.reps,
                s.recorded_at, s.updated_at
            from workout_set s
            """;

    private final JdbcClient jdbc;

    JdbcSetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(WorkoutSet set) {
        jdbc.sql("""
                        insert into workout_set (id, session_id, item_position, set_number, exercise_id, load_kg, reps,
                            recorded_at, updated_at)
                        values (:id, :sessionId, :item, :setNumber, :exerciseId, :loadKg, :reps, :recordedAt,
                            :updatedAt)
                        on conflict (id) do update set
                            exercise_id = excluded.exercise_id,
                            load_kg = excluded.load_kg,
                            reps = excluded.reps,
                            updated_at = excluded.updated_at
                        """)
                .param("id", set.id())
                .param("sessionId", set.sessionId())
                .param("item", set.itemPosition())
                .param("setNumber", set.setNumber())
                .param("exerciseId", set.exerciseId())
                .param("loadKg", set.loadKg())
                .param("reps", set.reps())
                .param("recordedAt", Timestamp.from(set.recordedAt()))
                .param("updatedAt", Timestamp.from(set.updatedAt()))
                .update();
    }

    @Override
    public Optional<WorkoutSet> findById(UUID id) {
        return jdbc.sql(SELECT + "where s.id = :id")
                .param("id", id)
                .query(JdbcSetRepository::toSet)
                .optional();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("delete from workout_set where id = :id").param("id", id).update();
    }

    @Override
    public List<WorkoutSet> findBySession(UUID sessionId) {
        return jdbc.sql(SELECT + "where s.session_id = :sessionId order by s.item_position, s.set_number")
                .param("sessionId", sessionId)
                .query(JdbcSetRepository::toSet)
                .list();
    }

    @Override
    public List<WorkoutSet> lastWithExercise(UUID clientId, UUID exerciseId, UUID exceptSession) {
        return jdbc.sql(SELECT + """
                        where s.exercise_id = :exerciseId and s.session_id = (
                            select ws.id from workout_session ws
                            join workout_set any_set on any_set.session_id = ws.id and any_set.exercise_id = :exerciseId
                            where ws.client_id = :clientId and ws.id <> :exceptSession and ws.finished_at is not null
                            order by ws.started_at desc limit 1)
                        order by s.set_number
                        """)
                .param("exerciseId", exerciseId)
                .param("clientId", clientId)
                .param("exceptSession", exceptSession)
                .query(JdbcSetRepository::toSet)
                .list();
    }

    private static WorkoutSet toSet(ResultSet row, int index) throws SQLException {
        return new WorkoutSet(
                row.getObject("id", UUID.class),
                row.getObject("session_id", UUID.class),
                row.getInt("item_position"),
                row.getInt("set_number"),
                row.getObject("exercise_id", UUID.class),
                row.getBigDecimal("load_kg"),
                row.getInt("reps"),
                row.getTimestamp("recorded_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
