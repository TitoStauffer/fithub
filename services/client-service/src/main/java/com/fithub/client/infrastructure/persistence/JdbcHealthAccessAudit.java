package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.link.HealthAccessAudit;
import com.fithub.commons.ids.UuidV7;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Auditoria das leituras da saúde no Postgres do client-service (migration V7). */
@Repository
class JdbcHealthAccessAudit implements HealthAccessAudit {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcHealthAccessAudit(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void record(UUID professionalId, UUID clientId, Instant at) {
        jdbc.sql("""
                        insert into health_access_log (id, professional_id, client_id, accessed_at)
                        values (:id, :professionalId, :clientId, :at)
                        """)
                .param("id", UuidV7.next(clock))
                .param("professionalId", professionalId)
                .param("clientId", clientId)
                .param("at", Timestamp.from(at))
                .update();
    }
}
