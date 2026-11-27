package com.fithub.nutrition.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expurgo da migration V16 (DEV-0153): só a cópia de quem tem vínculo ativo fica. O SQL é o próprio arquivo da migration,
 * rodado numa transação desfeita no fim, para não apagar o que outros testes gravaram no banco compartilhado.
 */
class ClientCopyPurgeMigrationTest extends ServiceIntegrationTest {

    private static final String MIGRATION = "/db/migration/V16__copia_de_cliente_so_com_vinculo.sql";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void theMigrationKeepsOnlyTheCopyOfAClientWithAnActiveLink() throws IOException {
        String purge = migration();
        UUID active = UUID.randomUUID();
        UUID ended = UUID.randomUUID();
        UUID none = UUID.randomUUID();

        List<Integer> left = transaction.execute(status -> {
            for (UUID client : List.of(active, ended, none)) {
                jdbc.sql("insert into client (client_id, name, updated_at) values (:id, 'Ana', now())")
                        .param("id", client)
                        .update();
            }
            link(active, "ACTIVE");
            link(ended, "ENDED");
            jdbc.sql(purge).update();
            List<Integer> counts = List.of(copies(active), copies(ended), copies(none));
            status.setRollbackOnly();
            return counts;
        });

        assertThat(left).containsExactly(1, 0, 0);
    }

    private void link(UUID client, String status) {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at, ended_at)
                        values (:link, :client, :professional, :status, now(),
                                case when :status = 'ENDED' then now() end)
                        """)
                .param("link", UUID.randomUUID())
                .param("client", client)
                .param("professional", UUID.randomUUID())
                .param("status", status)
                .update();
    }

    private int copies(UUID client) {
        return jdbc.sql("select count(*) from client where client_id = :id")
                .param("id", client)
                .query(Integer.class)
                .single();
    }

    private String migration() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(in).as(MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
