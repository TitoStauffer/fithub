package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.account.AccountErasure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Exclusão da conta do cliente no Postgres do client-service (DEV-0105, ADR-012, migration V13). A ordem segue as
 * chaves estrangeiras: consentimentos antes dos vínculos, e as fotos saem com a avaliação ({@code on delete cascade}).
 */
@Repository
class JdbcAccountErasure implements AccountErasure {

    private final JdbcClient jdbc;

    JdbcAccountErasure(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void erase(UUID clientId, String email, Instant at) {
        jdbc.sql("delete from consent where client_id = :clientId")
                .param("clientId", clientId)
                .update();
        // O convite é do profissional e fica com ele, mas sem o e-mail de quem o aceitou.
        jdbc.sql("""
                        update invitation set email = null
                         where id in (select invitation_id from client_link where client_id = :clientId)
                        """).param("clientId", clientId).update();
        if (email != null) {
            jdbc.sql("update invitation set email = null where lower(email) = lower(:email)")
                    .param("email", email)
                    .update();
        }
        jdbc.sql("delete from client_link where client_id = :clientId")
                .param("clientId", clientId)
                .update();
        jdbc.sql("delete from body_assessment where client_id = :clientId")
                .param("clientId", clientId)
                .update();
        String account = clientId.toString();
        jdbc.sql("delete from client_health where account_id = :account")
                .param("account", account)
                .update();
        jdbc.sql("delete from client_profile where account_id = :account")
                .param("account", account)
                .update();
        jdbc.sql("""
                        insert into deleted_account (client_id, deleted_at) values (:clientId, :at)
                        on conflict (client_id) do update set deleted_at = excluded.deleted_at
                        """)
                .param("clientId", clientId)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public List<DeletedAccount> deletedAccounts() {
        return jdbc.sql("select client_id, deleted_at from deleted_account order by deleted_at, client_id")
                .query((row, index) -> new DeletedAccount(
                        row.getObject("client_id", UUID.class),
                        row.getTimestamp("deleted_at").toInstant()))
                .list();
    }

    @Override
    public boolean restorePending() {
        return Boolean.TRUE.equals(jdbc.sql("select exists (select 1 from restore_pending)")
                .query(Boolean.class)
                .single());
    }

    @Override
    public void clearRestorePending() {
        jdbc.sql("delete from restore_pending").update();
    }

    @Override
    public int purgeDeletedBefore(Instant cutoff) {
        return jdbc.sql("delete from deleted_account where deleted_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
