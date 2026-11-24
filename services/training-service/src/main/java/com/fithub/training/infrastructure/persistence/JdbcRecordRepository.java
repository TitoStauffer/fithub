package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.record.ManualRecord;
import com.fithub.training.domain.record.PersonalRecords;
import com.fithub.training.domain.record.RecordRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Marcas para os recordes no Postgres do training-service: séries (V11) e recordes manuais (V13). */
@Repository
class JdbcRecordRepository implements RecordRepository {

    private final JdbcClient jdbc;

    JdbcRecordRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PersonalRecords.Performance> performancesOf(UUID clientId) {
        // A data da série é o dia do treino no fuso do Brasil, o mesmo do prazo de correção.
        return jdbc.sql("""
                        select s.exercise_id, s.load_kg, s.reps,
                            (ws.started_at at time zone 'America/Sao_Paulo')::date as performed_on,
                            'SET' as source, s.id
                        from workout_set s
                        join workout_session ws on ws.id = s.session_id
                        where ws.client_id = :clientId and s.reps >= 1
                        union all
                        select exercise_id, load_kg, reps, performed_on, 'MANUAL' as source, id
                        from manual_record
                        where client_id = :clientId
                        order by performed_on, id
                        """)
                .param("clientId", clientId)
                .query((row, index) -> new PersonalRecords.Performance(
                        row.getObject("exercise_id", UUID.class),
                        row.getBigDecimal("load_kg"),
                        row.getInt("reps"),
                        row.getObject("performed_on", LocalDate.class),
                        PersonalRecords.Source.valueOf(row.getString("source")),
                        row.getObject("id", UUID.class)))
                .list();
    }

    @Override
    public void saveManual(ManualRecord record) {
        jdbc.sql("""
                        insert into manual_record (id, client_id, exercise_id, load_kg, reps, performed_on, created_at)
                        values (:id, :clientId, :exerciseId, :loadKg, :reps, :performedOn, :createdAt)
                        """)
                .param("id", record.id())
                .param("clientId", record.clientId())
                .param("exerciseId", record.exerciseId())
                .param("loadKg", record.loadKg())
                .param("reps", record.reps())
                .param("performedOn", record.performedOn())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    @Override
    public Optional<ManualRecord> findManual(UUID id) {
        return jdbc.sql("""
                        select id, client_id, exercise_id, load_kg, reps, performed_on, created_at
                        from manual_record where id = :id
                        """)
                .param("id", id)
                .query(JdbcRecordRepository::toManual)
                .optional();
    }

    @Override
    public void deleteManual(UUID id) {
        jdbc.sql("delete from manual_record where id = :id").param("id", id).update();
    }

    private static ManualRecord toManual(ResultSet row, int index) throws SQLException {
        return new ManualRecord(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("exercise_id", UUID.class),
                row.getBigDecimal("load_kg"),
                row.getInt("reps"),
                row.getObject("performed_on", LocalDate.class),
                row.getTimestamp("created_at").toInstant());
    }
}
