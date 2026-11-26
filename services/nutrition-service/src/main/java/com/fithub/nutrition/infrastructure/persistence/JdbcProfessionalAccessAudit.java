package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.commons.ids.UuidV7;
import com.fithub.nutrition.domain.access.ProfessionalAccessAudit;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Auditoria das leituras de dados sensíveis no Postgres do nutrition-service (migration V14). */
@Repository
class JdbcProfessionalAccessAudit implements ProfessionalAccessAudit {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcProfessionalAccessAudit(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void record(UUID professionalId, UUID clientId, Resource resource, Instant at) {
        jdbc.sql("""
                        insert into professional_access_log (id, professional_id, client_id, resource, accessed_at)
                        values (:id, :professionalId, :clientId, :resource, :at)
                        """)
                .param("id", UuidV7.next(clock))
                .param("professionalId", professionalId)
                .param("clientId", clientId)
                .param("resource", resource.name())
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public List<AccessRecord> logOf(UUID clientId) {
        return jdbc.sql("""
                        select professional_id, resource, accessed_at from professional_access_log
                         where client_id = :clientId order by accessed_at desc, id desc
                        """)
                .param("clientId", clientId)
                .query((row, index) -> new AccessRecord(
                        row.getObject("professional_id", UUID.class),
                        Resource.valueOf(row.getString("resource")),
                        row.getTimestamp("accessed_at").toInstant()))
                .list();
    }

    @Override
    public int purgeBefore(Instant cutoff) {
        return jdbc.sql("delete from professional_access_log where accessed_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
