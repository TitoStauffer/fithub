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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Vínculos e consentimentos no Postgres do client-service (migration V5). */
@Repository
class JdbcLinkRepository implements LinkRepository {

    /** O último termo aceito no vínculo: um reaceite (DEV-0160) grava uma linha nova, e vale a mais recente. */
    private static final String LATEST_CONSENT =
            "select c.terms_version from consent c where c.link_id = l.id order by c.granted_at desc, c.id desc limit 1";

    private static final String COLUMNS =
            "id, client_id, professional_id, professional_type, status, invitation_id, started_at, ended_at, ended_by";

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcLinkRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Link> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from client_link where id = :id")
                .param("id", id)
                .query(JdbcLinkRepository::map)
                .optional();
    }

    @Override
    public boolean hasActiveLink(UUID professionalId, UUID clientId) {
        return jdbc.sql("""
                        select exists (select 1 from client_link
                                        where professional_id = :professionalId and client_id = :clientId
                                          and status = 'ACTIVE')
                        """)
                .param("professionalId", professionalId)
                .param("clientId", clientId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<WithProfessional> listByClient(UUID clientId) {
        return jdbc.sql("select " + prefixed("l") + ", p.name as professional_name, (" + LATEST_CONSENT
                        + ") as consent_version"
                        + " from client_link l join professional p on p.id = l.professional_id"
                        + " where l.client_id = :clientId order by l.started_at desc, l.id desc")
                .param("clientId", clientId)
                .query((row, index) -> new WithProfessional(
                        map(row, index), row.getString("professional_name"), row.getString("consent_version")))
                .list();
    }

    @Override
    public List<ActiveClient> listActiveByProfessional(UUID professionalId) {
        // O nome vem do perfil do cliente (o aceite exige o perfil); nada de saúde nesta lista.
        return jdbc.sql("""
                        select l.id, l.client_id, c.name, l.started_at
                          from client_link l join client_profile c on c.account_id = l.client_id::text
                         where l.professional_id = :professionalId and l.status = 'ACTIVE'
                         order by l.started_at desc, l.id desc
                        """)
                .param("professionalId", professionalId)
                .query((row, index) -> new ActiveClient(
                        row.getObject("id", UUID.class),
                        row.getObject("client_id", UUID.class),
                        row.getString("name"),
                        row.getTimestamp("started_at").toInstant()))
                .list();
    }

    @Override
    public boolean end(Link ended) {
        return jdbc.sql("""
                                update client_link set status = 'ENDED', ended_at = :endedAt, ended_by = :endedBy
                                 where id = :id and status = 'ACTIVE'
                                """)
                        .param("endedAt", Timestamp.from(ended.endedAt()))
                        .param("endedBy", ended.endedBy().name())
                        .param("id", ended.id())
                        .update()
                == 1;
    }

    private static String prefixed(String alias) {
        return java.util.Arrays.stream(COLUMNS.split(", "))
                .map(column -> alias + "." + column)
                .collect(java.util.stream.Collectors.joining(", "));
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
        addConsent(link, scope, termsVersion, grantedAt);
    }

    @Override
    public Optional<String> consentVersion(UUID linkId) {
        return jdbc.sql(
                        "select terms_version from consent where link_id = :linkId order by granted_at desc, id desc limit 1")
                .param("linkId", linkId)
                .query(String.class)
                .optional();
    }

    @Override
    public void addConsent(Link link, Set<SharedData> scope, String termsVersion, Instant grantedAt) {
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
        Timestamp endedAt = row.getTimestamp("ended_at");
        String endedBy = row.getString("ended_by");
        return new Link(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                row.getObject("professional_id", UUID.class),
                ProfessionalType.valueOf(row.getString("professional_type")),
                Link.Status.valueOf(row.getString("status")),
                row.getObject("invitation_id", UUID.class),
                row.getTimestamp("started_at").toInstant(),
                endedAt == null ? null : endedAt.toInstant(),
                endedBy == null ? null : Link.Party.valueOf(endedBy));
    }
}
