package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.application.EraseDeletedClient;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Consumo do {@code ContaExcluida} no training (DEV-0162, ADR-012): o que é do cliente sai, as fichas do personal
 * ficam sem o id dele, o dado de outro cliente fica intacto, a reentrega não falha e a lista de contas excluídas tem
 * expurgo de 30 dias.
 */
class AccountDeletionConsumerTest extends ServiceIntegrationTest {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EraseDeletedClient eraseDeletedClient;

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

    /** Ids do que foi semeado para um cliente. */
    record Seeded(UUID client, UUID personal, UUID personalPlan, UUID ownPlan) {}

    private Seeded seed() {
        UUID client = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        UUID exercise = UUID.randomUUID();
        jdbc.sql("""
                        insert into exercise (id, kind, owner_id, name, equipment, created_at, updated_at)
                        values (:id, 'OWN', :owner, 'Supino teste', 'BARBELL', now(), now())
                        """).param("id", exercise).param("owner", personal).update();
        UUID personalPlan = plan(client, personal);
        UUID ownPlan = plan(client, null);
        session(client, personalPlan, exercise);
        session(client, ownPlan, exercise);
        jdbc.sql("""
                        insert into manual_record (id, client_id, exercise_id, load_kg, reps, performed_on, created_at)
                        values (:id, :c, :e, 80, 5, date '2026-11-20', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("e", exercise)
                .update();
        jdbc.sql("""
                        insert into mobility_restriction (id, client_id, region, description, recorded_by, created_at,
                                                          updated_at)
                        values (:id, :c, 'KNEE', 'dor no joelho', :p, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", personal)
                .update();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :c, :p, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", personal)
                .update();
        jdbc.sql("insert into client (client_id, name, updated_at) values (:c, 'Ana Aluna', now())")
                .param("c", client)
                .update();
        return new Seeded(client, personal, personalPlan, ownPlan);
    }

    /** Ficha publicada com uma versão; {@code personal} nulo é a ficha própria do aluno. */
    private UUID plan(UUID client, UUID personal) {
        UUID plan = UUID.randomUUID();
        jdbc.sql("""
                        insert into workout_plan (id, client_id, personal_id, status, author, created_at, updated_at)
                        values (:id, :c, :p, 'PUBLISHED', :author, now(), now())
                        """)
                .param("id", plan)
                .param("c", client)
                .param("p", personal)
                .param("author", personal == null ? "CLIENT" : "PERSONAL")
                .update();
        jdbc.sql("""
                        insert into workout_plan_version (id, plan_id, number, name, starts_on, validity_weeks,
                                                          published_at, created_at)
                        values (:id, :plan, 1, 'Ficha A', date '2026-11-01', 8, now(), now())
                        """).param("id", UUID.randomUUID()).param("plan", plan).update();
        return plan;
    }

    private void session(UUID client, UUID plan, UUID exercise) {
        UUID session = UUID.randomUUID();
        jdbc.sql("""
                        insert into workout_session (id, client_id, plan_id, version_id, division_position, started_at,
                                                     finished_at, comment, feeling)
                        select :id, :c, :plan, v.id, 0, now(), now(), 'cansado', 4
                          from workout_plan_version v where v.plan_id = :plan
                        """)
                .param("id", session)
                .param("c", client)
                .param("plan", plan)
                .update();
        jdbc.sql("""
                        insert into workout_set (id, session_id, item_position, set_number, exercise_id, load_kg, reps,
                                                 recorded_at, updated_at)
                        values (:id, :s, 0, 1, :e, 60, 10, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("s", session)
                .param("e", exercise)
                .update();
    }

    private int count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    /** Linhas do cliente em cada tabela que a exclusão deve esvaziar. */
    private List<Integer> leftovers(UUID client) {
        return List.of(
                count(
                        "select count(*) from workout_set where session_id in"
                                + " (select id from workout_session where client_id = :id)",
                        client),
                count("select count(*) from workout_session where client_id = :id", client),
                count("select count(*) from manual_record where client_id = :id", client),
                count("select count(*) from mobility_restriction where client_id = :id", client),
                count("select count(*) from workout_plan where client_id = :id", client),
                count("select count(*) from client_link where client_id = :id", client),
                count("select count(*) from client where client_id = :id", client));
    }

    private void publish(String eventId, UUID client) {
        String payload = """
                {"clientId": "%s", "occurredAt": "2026-11-25T12:00:00Z"}
                """.formatted(client);
        assertThat(EventContracts.validate(EventContract.ACCOUNT_DELETED_V1, payload))
                .isEmpty();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(
                "fithub.client",
                "client.account-deleted.v1",
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private void awaitProcessed(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.sql("select count(*) from fithub_processed_event where event_id = cast(:id as uuid)")
                    .param("id", eventId)
                    .query(Integer.class)
                    .single();
            if (count > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("O evento não foi processado.");
    }

    @Test
    void apagaTudoDoClienteEAsFichasDoPersonalFicamSemOIdDele() throws Exception {
        Seeded deleted = seed();
        Seeded other = seed();
        assertThat(leftovers(deleted.client())).containsExactly(2, 2, 1, 1, 2, 1, 1);
        String eventId = UUID.randomUUID().toString();

        publish(eventId, deleted.client());
        awaitProcessed(eventId);

        assertThat(leftovers(deleted.client())).containsOnly(0);
        assertThat(leftovers(other.client())).containsExactly(2, 2, 1, 1, 2, 1, 1);
        // A ficha do personal fica, com as versões, e o id do aluno trocado por outro que não é o de ninguém.
        UUID anonymous = jdbc.sql("select client_id from workout_plan where id = :id")
                .param("id", deleted.personalPlan())
                .query(UUID.class)
                .single();
        assertThat(anonymous).isNotEqualTo(deleted.client()).isNotEqualTo(other.client());
        assertThat(count("select count(*) from workout_plan_version where plan_id = :id", deleted.personalPlan()))
                .isEqualTo(1);
        assertThat(count("select count(*) from workout_plan where id = :id", deleted.ownPlan()))
                .isZero();
        assertThat(count("select count(*) from workout_plan where client_id = :id", other.client()))
                .isEqualTo(2);
        assertThat(count("select count(*) from deleted_account where client_id = :id", deleted.client()))
                .isEqualTo(1);
    }

    @Test
    void reentregaEOutroEventoDaMesmaContaNaoFalham() throws Exception {
        Seeded deleted = seed();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        publish(first, deleted.client());
        publish(first, deleted.client());
        publish(second, deleted.client());
        awaitProcessed(second);

        assertThat(leftovers(deleted.client())).containsOnly(0);
        assertThat(count("select count(*) from deleted_account where client_id = :id", deleted.client()))
                .isEqualTo(1);
    }

    @Test
    void expurgoTiraDaListaSoQuemFoiExcluidoHaMaisDe30Dias() {
        Instant now = Instant.now();
        UUID old = insertDeleted(now.minus(Duration.ofDays(31)));
        UUID recent = insertDeleted(now.minus(Duration.ofDays(29)));

        eraseDeletedClient.purgeDeletedAccounts();

        assertThat(count("select count(*) from deleted_account where client_id = :id", old))
                .isZero();
        assertThat(count("select count(*) from deleted_account where client_id = :id", recent))
                .isEqualTo(1);
    }

    private UUID insertDeleted(Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, :at)")
                .param("id", id)
                .param("at", Timestamp.from(at))
                .update();
        return id;
    }
}
