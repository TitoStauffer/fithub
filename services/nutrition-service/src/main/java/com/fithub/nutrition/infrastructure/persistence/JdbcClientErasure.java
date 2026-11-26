package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.account.ClientErasure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Exclusão do cliente no Postgres do nutrition-service (DEV-0163, ADR-012, migration V13). As trocas saem com o
 * registro de refeição ({@code on delete cascade}); os registros saem antes de os planos ficarem anônimos.
 */
@Repository
class JdbcClientErasure implements ClientErasure {

    private final JdbcClient jdbc;

    JdbcClientErasure(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void erase(UUID clientId, Instant at) {
        jdbc.sql("delete from meal_log where client_id = :c")
                .param("c", clientId)
                .update();
        jdbc.sql("delete from food_restriction where client_id = :c")
                .param("c", clientId)
                .update();
        // Os planos das nutricionistas ficam com elas (BR-05). Um id aleatório só para todos os planos desta pessoa: a
        // nutricionista continua vendo que eram do mesmo paciente, sem nenhuma ligação com a conta.
        jdbc.sql("update meal_plan set client_id = :anonymous where client_id = :c")
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
    public int purgeDeletedBefore(Instant cutoff) {
        return jdbc.sql("delete from deleted_account where deleted_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
