package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkRepository;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.commons.ids.UuidV7;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Vínculos e consentimentos no Postgres do client-service (migration V5). */
@Repository
class JdbcLinkRepository implements LinkRepository {

    private static final String COLUMNS =
            "id, client_id, professional_id, professional_type, status, invitation_id, started_at";

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcLinkRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Link> findActive(UUID clientId, ProfessionalType type) {
        return jdbc.sql("select " + COLUMNS
                        + " from client_link where client_id = :clientId and professional_type = :type"
                        + " and status = 'ACTIVE'")
                .param("clientId", clientId)
                .param("type", type.name())
                .query(JdbcLinkRepository::map)
                .optional();
    }

    @Override
    public void saveWithConsent(Link link, Set<SharedData> scope, String termsVersion, Instant grantedAt) {
        try {
            jdbc.sql("""
                            insert into client_link (id, client_id, professional_id, professional_type, status,
                                invitation_id, started_at)
                            values (:id, :clientId, :professionalId, :type, :status, :invitationId, :startedAt)
                            """)
                    .param("id", link.id())
                    .param("clientId", link.clientId())
                    .param("professionalId", link.professionalId())
                    .param("type", link.type().name())
                    .param("status", link.status().name())
                    .param("invitationId", link.invitationId())
                    .param("startedAt", Timestamp.from(link.startedAt()))
                    .update();
        } catch (DuplicateKeyException duplicate) {
            // O índice parcial ux_client_link_active_type recusou: outro aceite do mesmo tipo ganhou a corrida.
            throw new ActiveLinkConflict(duplicate);
        }
        jdbc.sql("""
                        insert into consent (id, link_id, client_id, scope, terms_version, granted_at)
                        values (:id, :linkId, :clientId, :scope, :termsVersion, :grantedAt)
                        """)
                .param("id", UuidV7.next(clock))
                .param("linkId", link.id())
                .param("clientId", link.clientId())
                .param("scope", scope.stream().map(Enum::name).toArray(String[]::new))
                .param("termsVersion", termsVersion)
                .param("grantedAt", Timestamp.from(grantedAt))
                .update();
    }

    private static Link map(ResultSet row, int index) throws SQLException {
        return new Link(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("professional_id", UUID.class),
                ProfessionalType.valueOf(row.getString("professional_type")),
                Link.Status.valueOf(row.getString("status")),
                row.getObject("invitation_id", UUID.class),
                row.getTimestamp("started_at").toInstant());
    }
}
