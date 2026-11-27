package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.account.ClientErasure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Exclusão do cliente no Postgres do training-service (DEV-0162, ADR-012, migration V16). As séries saem antes das
 * sessões, e as sessões antes das fichas; as versões, os treinos e os itens da ficha própria saem em cascata.
 */
@Repository
class JdbcClientErasure implements ClientErasure {

    private final JdbcClient jdbc;

    JdbcClientErasure(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void erase(UUID clientId, Instant at) {
        jdbc.sql("delete from workout_set where session_id in (select id from workout_session where client_id = :c)")
                .param("c", clientId)
                .update();
        jdbc.sql("delete from workout_session where client_id = :c")
                .param("c", clientId)
                .update();
        jdbc.sql("delete from manual_record where client_id = :c")
                .param("c", clientId)
                .update();
        jdbc.sql("delete from mobility_restriction where client_id = :c")
                .param("c", clientId)
                .update();
        jdbc.sql("delete from workout_plan where client_id = :c and author = 'CLIENT'")
                .param("c", clientId)
                .update();
        // As fichas dos personais ficam com eles (BR-05). Um id aleatório só para todas as fichas desta pessoa: o
        // personal continua vendo que eram do mesmo aluno, sem nenhuma ligação com a conta.
        jdbc.sql("update workout_plan set client_id = :anonymous where client_id = :c")
                .param("anonymous", UUID.randomUUID())
                .param("c", clientId)
                .update();
        jdbc.sql("delete from client_link where client_id = :c")
                .param("c", clientId)
                .update();
        jdbc.sql("delete from client where client_id = :c").param("c", clientId).update();
        jdbc.sql("""
                        insert into deleted_account (client_id, deleted_at) values (:c, :at)
                        on conflict (client_id) do update set deleted_at = excluded.deleted_at
                        """).param("c", clientId).param("at", Timestamp.from(at)).update();
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
    public boolean deletedAtOrAfter(UUID clientId, Instant occurredAt) {
        // O deleted_at é o occurredAt do ContaExcluida, do mesmo relógio do client-service que emite os eventos de
        // vínculo.
        return Boolean.TRUE.equals(
                jdbc.sql("select exists (select 1 from deleted_account where client_id = :c and deleted_at >= :at)")
                        .param("c", clientId)
                        .param("at", Timestamp.from(occurredAt))
                        .query(Boolean.class)
                        .single());
    }

    @Override
    public int purgeDeletedBefore(Instant cutoff) {
        return jdbc.sql("delete from deleted_account where deleted_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
