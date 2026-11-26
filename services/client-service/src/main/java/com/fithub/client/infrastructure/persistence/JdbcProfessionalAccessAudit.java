package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.link.ProfessionalAccessAudit;
import com.fithub.commons.ids.UuidV7;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Auditoria das leituras de dados sensíveis no Postgres do client-service (migrations V7 e V14). */
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
                        insert into health_access_log (id, professional_id, client_id, resource, accessed_at)
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
    public int purgeBefore(Instant cutoff) {
        return jdbc.sql("delete from health_access_log where accessed_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
