package com.fithub.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reaplicação das exclusões depois de restaurar um backup (DEV-0172, ADR-012, item 4): o expurgo periódico, que também
 * roda na subida, reapaga o dado de quem está na lista de contas excluídas, sem mudar a data da lista nem tocar em quem
 * não está nela, e sem publicar evento.
 */
@Import(ReapplyDeletionsTest.Tokens.class)
class ReapplyDeletionsTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DeletedAccountPurgeScheduler scheduler;

    @TestConfiguration
    static class Tokens {

        /** {@code conta-<uuid>} vira um JWT de cliente com esse {@code sub}; o resto é recusado. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!token.startsWith("conta-")) {
                    throw new BadJwtException("token inválido");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token.substring("conta-".length()))
                        .claim("realm_access", java.util.Map.of("roles", List.of("client")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
            };
        }
    }

    /** Cliente com perfil, saúde e uma avaliação, como um backup restaurado traria de volta. */
    private UUID client() throws Exception {
        UUID client = UUID.randomUUID();
        String token = "Bearer conta-" + client;
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HEALTH"}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(put("/clients/me/health")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditions\": [\"HYPERTENSION\"], \"notes\": \"joelho\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/clients/me/assessments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\": \"2026-11-20\", \"weightKg\": 64.0}"))
                .andExpect(status().isCreated());
        return client;
    }

    private List<Integer> leftovers(UUID client) {
        return List.of(
                count("select count(*) from client_profile where account_id = :id", client.toString()),
                count("select count(*) from client_health where account_id = :id", client.toString()),
                count("select count(*) from body_assessment where client_id = :id", client));
    }

    private int count(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    @Test
    void oExpurgoReapagaODadoDeQuemEstaNaListaSemMudarADataNemTocarNosOutros() throws Exception {
        UUID deleted = client();
        UUID other = client();
        Instant deletedAt = Instant.parse("2026-11-20T10:00:00Z");
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, :at)")
                .param("id", deleted)
                .param("at", Timestamp.from(deletedAt))
                .update();
        // A restauração gravou o marcador (infra/backup/restore.sh).
        jdbc.sql("insert into restore_pending (restored_at) values (now())").update();
        int events = count(
                "select count(*) from fithub_outbox_event where payload ->> 'clientId' = :id", deleted.toString());

        scheduler.purge();
        assertThat(jdbc.sql("select count(*) from restore_pending")
                        .query(Integer.class)
                        .single())
                .isZero();

        assertThat(leftovers(deleted)).containsOnly(0);
        assertThat(leftovers(other)).containsExactly(1, 1, 1);
        assertThat(jdbc.sql("select deleted_at from deleted_account where client_id = :id")
                        .param("id", deleted)
                        .query(Timestamp.class)
                        .single()
                        .toInstant())
                .isEqualTo(deletedAt);
        assertThat(count(
                        "select count(*) from fithub_outbox_event where payload ->> 'clientId' = :id",
                        deleted.toString()))
                .isEqualTo(events);
    }

    @Test
    void semRestauracaoODadoDeUmIdDaListaFica() throws Exception {
        // Quem também é profissional pode recriar o perfil de cliente depois de excluir a conta de cliente: sem
        // marcador de restauração, a reaplicação não toca nesse dado novo (Code Review do PR #316).
        jdbc.sql("delete from restore_pending").update();
        UUID recreated = client();
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, now() - interval '1 day')")
                .param("id", recreated)
                .update();

        scheduler.purge();

        assertThat(leftovers(recreated)).containsExactly(1, 1, 1);
    }
}
