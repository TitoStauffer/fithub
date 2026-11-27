package com.fithub.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
        mvc.perform(
                        put("/clients/me/health")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"consent\": true, \"termsVersion\": \"v1\", \"conditions\": [\"HYPERTENSION\"], \"notes\": \"joelho\"}"))
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
        // Data relativa: o expurgo roda com o relógio real antes de reaplicar, e uma data fixa sairia da lista depois
        // de
        // 30 dias (Code Review do PR #316). Microssegundos, a precisão do timestamptz, para a comparação exata.
        Instant deletedAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
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

    // DEV-0174: o convite pendente que voltou com o backup, enviado para o e-mail de quem excluiu a conta, perde o
    // e-mail na reaplicação, pelo hash guardado na lista (a reaplicação não tem o token). O hash fica na lista, e o
    // convite para outro e-mail continua.
    @Test
    void aReaplicacaoLimpaOEmailDosConvitesRestauradosPeloHashDaLista() {
        UUID deleted = UUID.randomUUID();
        String hash = sha256("restaurado-" + deleted + "@teste.fithub");
        jdbc.sql(
                        "insert into deleted_account (client_id, deleted_at, email_hash) values (:id, now() - interval '1 day', :h)")
                .param("id", deleted)
                .param("h", hash)
                .update();
        UUID professional = UUID.randomUUID();
        jdbc.sql("""
                        insert into professional (id, professional_type, name, invite_code, registered_at, updated_at)
                        values (:id, 'PERSONAL_TRAINER', 'Paulo Personal', :code, now(), now())
                        """).param("id", professional).param("code", inviteCode()).update();
        UUID restored =
                invitation(professional, "Restaurado-" + deleted.toString().toUpperCase() + "@Teste.Fithub");
        UUID other = invitation(professional, "outro-" + deleted + "@teste.fithub");
        jdbc.sql("insert into restore_pending (restored_at) values (now())").update();

        scheduler.purge();

        assertThat(emailOf(restored)).isNull();
        assertThat(emailOf(other)).isEqualTo("outro-" + deleted + "@teste.fithub");
        assertThat(jdbc.sql("select email_hash from deleted_account where client_id = :id")
                        .param("id", deleted)
                        .query(String.class)
                        .single())
                .isEqualTo(hash);
    }

    private UUID invitation(UUID professional, String email) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into invitation (id, professional_id, email, token_hash, code_hash, status, expires_at,
                                                created_at)
                        values (:id, :p, :email, :token, :code, 'PENDING', now() + interval '7 days', now())
                        """)
                .param("id", id)
                .param("p", professional)
                .param("email", email)
                .param("token", sha256(UUID.randomUUID().toString()))
                .param("code", sha256(UUID.randomUUID().toString()))
                .update();
        return id;
    }

    private String emailOf(UUID invitation) {
        return jdbc.sql("select email from invitation where id = :id")
                .param("id", invitation)
                .query(String.class)
                .list()
                .getFirst();
    }

    /** Código pessoal aleatório no alfabeto do convite (sem 0/O e 1/I). */
    private static String inviteCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(text.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
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
