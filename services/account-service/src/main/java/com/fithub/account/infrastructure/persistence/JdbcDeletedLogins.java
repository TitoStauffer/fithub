package com.fithub.account.infrastructure.persistence;

import com.fithub.account.domain.login.DeletedLogins;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lista {@code deleted_account} e marcador {@code restore_pending} do banco do account-service (DEV-0173). */
@Repository
class JdbcDeletedLogins implements DeletedLogins {

    private final JdbcClient jdbc;

    JdbcDeletedLogins(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID accountId, Instant deletedAt) {
        jdbc.sql("""
                        insert into deleted_account (client_id, deleted_at) values (:id, :at)
                        on conflict (client_id) do update set deleted_at = greatest(deleted_account.deleted_at, excluded.deleted_at)
                        """)
                .param("id", accountId)
                .param("at", Timestamp.from(deletedAt))
                .update();
    }

    @Override
    public List<UUID> accountIds() {
        return jdbc.sql("select client_id from deleted_account order by deleted_at, client_id")
                .query(UUID.class)
                .list();
    }

    @Override
    public int purgeDeletedBefore(Instant cutoff) {
        return jdbc.sql("delete from deleted_account where deleted_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
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
}
