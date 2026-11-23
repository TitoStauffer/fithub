package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.invitation.Invitation;
import com.fithub.client.domain.invitation.InvitationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Convites no Postgres do client-service (migration V4). Token e código só como hash. */
@Repository
class JdbcInvitationRepository implements InvitationRepository {

    private static final String COLUMNS =
            "id, professional_id, email, token_hash, code_hash, status, expires_at, created_at, cancelled_at";

    private final JdbcClient jdbc;

    JdbcInvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveNew(Invitation invitation) {
        // Sem alvo no "on conflict": colisão de token ou de código não grava e devolve 0 linhas.
        return jdbc.sql("""
                        insert into invitation (id, professional_id, email, token_hash, code_hash, status, expires_at,
                            created_at, cancelled_at)
                        values (:id, :professionalId, :email, :tokenHash, :codeHash, :status, :expiresAt, :createdAt,
                            :cancelledAt)
                        on conflict do nothing
                        """)
                        .param("id", invitation.id())
                        .param("professionalId", invitation.professionalId())
                        .param("email", invitation.email())
                        .param("tokenHash", invitation.tokenHash())
                        .param("codeHash", invitation.codeHash())
                        .param("status", invitation.status().name())
                        .param("expiresAt", Timestamp.from(invitation.expiresAt()))
                        .param("createdAt", Timestamp.from(invitation.createdAt()))
                        .param("cancelledAt", timestamp(invitation.cancelledAt()))
                        .update()
                == 1;
    }

    @Override
    public Optional<Invitation> findById(UUID id) {
        return find("id", id);
    }

    @Override
    public Optional<Invitation> findByTokenHash(String tokenHash) {
        return find("token_hash", tokenHash);
    }

    @Override
    public Optional<Invitation> findByCodeHash(String codeHash) {
        return find("code_hash", codeHash);
    }

    @Override
    public List<Invitation> listByProfessional(UUID professionalId) {
        return jdbc.sql("select " + COLUMNS
                        + " from invitation where professional_id = :professionalId order by created_at desc, id desc")
                .param("professionalId", professionalId)
                .query(JdbcInvitationRepository::map)
                .list();
    }

    @Override
    public void updateStatus(Invitation invitation) {
        jdbc.sql("update invitation set status = :status, cancelled_at = :cancelledAt where id = :id")
                .param("status", invitation.status().name())
                .param("cancelledAt", timestamp(invitation.cancelledAt()))
                .param("id", invitation.id())
                .update();
    }

    private Optional<Invitation> find(String column, Object value) {
        return jdbc.sql("select " + COLUMNS + " from invitation where " + column + " = :value")
                .param("value", value)
                .query(JdbcInvitationRepository::map)
                .optional();
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Invitation map(ResultSet row, int index) throws SQLException {
        Timestamp cancelledAt = row.getTimestamp("cancelled_at");
        return new Invitation(
                row.getObject("id", UUID.class),
                row.getObject("professional_id", UUID.class),
                row.getString("email"),
                row.getString("token_hash"),
                row.getString("code_hash"),
                Invitation.Status.valueOf(row.getString("status")),
                row.getTimestamp("expires_at").toInstant(),
                row.getTimestamp("created_at").toInstant(),
                cancelledAt == null ? null : cancelledAt.toInstant());
    }
}
