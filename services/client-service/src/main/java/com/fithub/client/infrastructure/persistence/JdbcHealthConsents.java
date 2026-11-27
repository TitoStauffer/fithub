package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.profile.HealthConsents;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aceites do termo do perfil de saúde no Postgres do client-service (migration V17). */
@Repository
class JdbcHealthConsents implements HealthConsents {

    private final JdbcClient jdbc;

    JdbcHealthConsents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean accepted(String accountId, String termsVersion) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        select exists (select 1 from health_consent
                                        where account_id = :accountId and terms_version = :termsVersion)
                        """)
                .param("accountId", accountId)
                .param("termsVersion", termsVersion)
                .query(Boolean.class)
                .single());
    }

    @Override
    public void accept(String accountId, String termsVersion, Instant grantedAt) {
        // Dois envios ao mesmo tempo gravam um aceite só; vale o primeiro momento.
        jdbc.sql("""
                        insert into health_consent (account_id, terms_version, granted_at)
                        values (:accountId, :termsVersion, :grantedAt)
                        on conflict do nothing
                        """)
                .param("accountId", accountId)
                .param("termsVersion", termsVersion)
                .param("grantedAt", Timestamp.from(grantedAt))
                .update();
    }
}
