package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.account.TrainingExportReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Leitura das fichas e das sessões do cliente para a exportação (DEV-0166), sempre filtrada pelo cliente. */
@Repository
class JdbcTrainingExportReader implements TrainingExportReader {

    private final JdbcClient jdbc;

    JdbcTrainingExportReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> planIdsOf(UUID clientId) {
        return jdbc.sql("select id from workout_plan where client_id = :c order by created_at desc, id desc")
                .param("c", clientId)
                .query(UUID.class)
                .list();
    }

    @Override
    public List<ExportedSession> sessionsOf(UUID clientId) {
        Map<UUID, List<ExportedSet>> setsBySession = new HashMap<>();
        jdbc.sql("""
                        select s.session_id, s.item_position, s.set_number, s.exercise_id, e.name, s.load_kg, s.reps,
                               s.recorded_at
                          from workout_set s
                          join workout_session ws on ws.id = s.session_id
                          join exercise e on e.id = s.exercise_id
                         where ws.client_id = :c
                         order by s.item_position, s.set_number
                        """)
                .param("c", clientId)
                .query((row, index) -> setsBySession
                        .computeIfAbsent(row.getObject("session_id", UUID.class), id -> new ArrayList<>())
                        .add(new ExportedSet(
                                row.getInt("item_position"),
                                row.getInt("set_number"),
                                row.getObject("exercise_id", UUID.class),
                                row.getString("name"),
                                row.getBigDecimal("load_kg"),
                                row.getInt("reps"),
                                row.getTimestamp("recorded_at").toInstant())))
                .list();
        return jdbc.sql("""
                        select ws.id, ws.plan_id, v.number, ws.division_position, ws.started_at, ws.finished_at,
                               ws.comment, ws.feeling
                          from workout_session ws
                          join workout_plan_version v on v.id = ws.version_id
                         where ws.client_id = :c
                         order by ws.started_at desc, ws.id desc
                        """)
                .param("c", clientId)
                .query((row, index) -> {
                    UUID id = row.getObject("id", UUID.class);
                    return new ExportedSession(
                            id,
                            row.getObject("plan_id", UUID.class),
                            row.getInt("number"),
                            row.getInt("division_position"),
                            row.getTimestamp("started_at").toInstant(),
                            instant(row.getTimestamp("finished_at")),
                            row.getString("comment"),
                            row.getObject("feeling", Integer.class),
                            setsBySession.getOrDefault(id, List.of()));
                })
                .list();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
