package com.fithub.nutrition.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Reaplicação das exclusões depois de restaurar um backup (DEV-0172, ADR-012, item 4): o expurgo periódico, que também
 * roda na subida, reapaga o dado de quem está na lista de contas excluídas, sem mudar a data da lista nem tocar em quem
 * não está nela.
 */
class ReapplyDeletionsTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DeletedAccountPurgeScheduler scheduler;

    @TestConfiguration
    static class Tokens {

        /** Este teste não chama a API: nenhum token é aceito. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("token inválido");
            };
        }
    }

    /** Paciente com restrição alimentar e a projeção local, como um backup restaurado traria de volta. */
    private UUID client() {
        UUID client = UUID.randomUUID();
        jdbc.sql("""
                        insert into food_restriction (id, client_id, kind, description, terms, recorded_by, created_at,
                                                      updated_at)
                        values (:id, :c, 'ALLERGY', 'amendoim', array['amendoim'], :p, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", UUID.randomUUID())
                .update();
        jdbc.sql("insert into client (client_id, name, updated_at) values (:c, 'Ana Paciente', now())")
                .param("c", client)
                .update();
        return client;
    }

    private List<Integer> leftovers(UUID client) {
        return List.of(
                count("select count(*) from food_restriction where client_id = :id", client),
                count("select count(*) from client where client_id = :id", client));
    }

    private int count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    @Test
    void oExpurgoReapagaODadoDeQuemEstaNaListaSemMudarADataNemTocarNosOutros() {
        UUID deleted = client();
        UUID other = client();
        Instant deletedAt = Instant.now().minusSeconds(86_400);
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, :at)")
                .param("id", deleted)
                .param("at", Timestamp.from(deletedAt))
                .update();

        scheduler.purge();

        assertThat(leftovers(deleted)).containsOnly(0);
        assertThat(leftovers(other)).containsExactly(1, 1);
        assertThat(jdbc.sql("select deleted_at from deleted_account where client_id = :id")
                        .param("id", deleted)
                        .query(Timestamp.class)
                        .single()
                        .toInstant())
                .isCloseTo(deletedAt, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
    }
}
