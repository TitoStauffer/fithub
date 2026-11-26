package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.account.PrivacyRecords;
import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.ProfessionalAccessAudit;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consentimentos (V5) e leituras da saúde (V7) do cliente, para a exportação (DEV-0104). */
@Repository
class JdbcPrivacyRecords implements PrivacyRecords {

    private final JdbcClient jdbc;

    JdbcPrivacyRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Consent> consentsOf(UUID clientId) {
        return jdbc.sql("""
                        select link_id, scope, terms_version, granted_at from consent
                         where client_id = :clientId order by granted_at desc, id desc
                        """)
                .param("clientId", clientId)
                .query((row, index) -> new Consent(
                        row.getObject("link_id", UUID.class),
                        scope(row.getArray("scope")),
                        row.getString("terms_version"),
                        row.getTimestamp("granted_at").toInstant()))
                .list();
    }

    @Override
    public List<HealthRead> healthReadsOf(UUID clientId) {
        // O nome vem da projeção de profissionais; quem saiu da projeção aparece sem nome.
        return jdbc.sql("""
                        select a.professional_id, p.name, a.accessed_at
                          from health_access_log a left join professional p on p.id = a.professional_id
                         where a.client_id = :clientId and a.resource = 'HEALTH'
                         order by a.accessed_at desc, a.id desc
                        """)
                .param("clientId", clientId)
                .query((row, index) -> new HealthRead(
                        row.getObject("professional_id", UUID.class),
                        row.getString("name"),
                        row.getTimestamp("accessed_at").toInstant()))
                .list();
    }

    @Override
    public List<AccessRecord> accessLogOf(UUID clientId) {
        return jdbc.sql("""
                        select a.professional_id, p.name, a.resource, a.accessed_at
                          from health_access_log a left join professional p on p.id = a.professional_id
                         where a.client_id = :clientId
                         order by a.accessed_at desc, a.id desc
                        """)
                .param("clientId", clientId)
                .query((row, index) -> new AccessRecord(
                        row.getObject("professional_id", UUID.class),
                        row.getString("name"),
                        ProfessionalAccessAudit.Resource.valueOf(row.getString("resource")),
                        row.getTimestamp("accessed_at").toInstant()))
                .list();
    }

    private static Set<SharedData> scope(Array array) throws SQLException {
        Set<SharedData> scope = EnumSet.noneOf(SharedData.class);
        Arrays.stream((String[]) array.getArray()).map(SharedData::valueOf).forEach(scope::add);
        return scope;
    }
}
