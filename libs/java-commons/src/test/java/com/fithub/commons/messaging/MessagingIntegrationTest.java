package com.fithub.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.fithub.commons.testapp.TestApplication;

/** Outbox e consumo idempotente contra Postgres e RabbitMQ reais (DEV-0014). */
@Testcontainers
@SpringBootTest(classes = TestApplication.class, properties = {
        "fithub.messaging.exchange=" + MessagingIntegrationTest.EXCHANGE,
        "fithub.messaging.publisher.poll-interval=200ms",
})
class MessagingIntegrationTest {

    static final String EXCHANGE = "fithub.teste";
    private static final String ROUTING_KEY = "teste.algo-aconteceu.v1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.12");

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.3.8-management");

    @Autowired
    private OutboxWriter outbox;
    @Autowired
    private ProcessedEvents processedEvents;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbc;
    private Queue queue;

    @BeforeEach
    void bindQueue() {
        jdbc = JdbcClient.create(dataSource);
        queue = new AnonymousQueue();
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(EXCHANGE)).with("teste.#");
        rabbitAdmin.declareBinding(binding);
    }

    // --- Outbox transacional ---

    @Test
    void eventWrittenInACommittedTransactionIsPublishedWithItsIdAsMessageId() {
        UUID id = transaction.execute(status -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-1")));

        Message message = rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(ROUTING_KEY);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"clienteId\": \"c-1\"}");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                jdbc.sql("select published_at is not null from fithub_outbox_event where id = ?").param(id).query(Boolean.class).single())
                .isTrue());
    }

    @Test
    void rolledBackTransactionLeavesNoEventAndPublishesNothing() {
        UUID id = transaction.execute(status -> {
            UUID written = outbox.append(ROUTING_KEY, Map.of("clienteId", "c-2"));
            status.setRollbackOnly();
            return written;
        });

        assertThat(rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(2).toMillis())).isNull();
        assertThat(jdbc.sql("select count(*) from fithub_outbox_event where id = ?").param(id).query(Integer.class).single()).isZero();
    }

    @Test
    void writingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-3")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void eventThatTheBrokerRejectsStaysPendingWithTheError() {
        UUID id = UUID.randomUUID();
        // Exchange inexistente: o broker fecha o canal e a confirmação não chega.
        jdbc.sql("insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at) values (?, 'fithub.inexistente', ?, '{}'::jsonb, now())")
                .params(id, ROUTING_KEY).update();
        try {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                    jdbc.sql("select attempts from fithub_outbox_event where id = ?").param(id).query(Integer.class).single())
                    .isPositive());
            Map<String, Object> row = jdbc.sql("select published_at, last_error from fithub_outbox_event where id = ?").param(id).query().singleRow();
            assertThat(row.get("published_at")).isNull();
            assertThat(row.get("last_error")).isNotNull();
        } finally {
            jdbc.sql("delete from fithub_outbox_event where id = ?").param(id).update();
        }
    }

    // --- Consumo idempotente ---

    @Test
    void repeatedEventIsProcessedOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();

        assertThat(processedEvents.processOnce("projecao-vinculos", eventId, runs::incrementAndGet)).isTrue();
        assertThat(processedEvents.processOnce("projecao-vinculos", eventId, runs::incrementAndGet)).isFalse();
        assertThat(runs).hasValue(1);
        // Outro consumidor do mesmo evento processa a sua parte.
        assertThat(processedEvents.processOnce("outro-consumidor", eventId, runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void failedHandlerDoesNotMarkTheEventSoARedeliveryProcessesIt() {
        UUID eventId = UUID.randomUUID();
        assertThatThrownBy(() -> processedEvents.processOnce("projecao-vinculos", eventId, () -> {
            throw new IllegalStateException("falha no handler");
        })).isInstanceOf(IllegalStateException.class);

        AtomicInteger runs = new AtomicInteger();
        assertThat(processedEvents.processOnce("projecao-vinculos", eventId, runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void publishedMessageIsDeduplicatedByItsMessageId() {
        transaction.execute(status -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-4")));
        Message message = rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(message).isNotNull();
        AtomicInteger runs = new AtomicInteger();

        processedEvents.processOnce("consumidor-amqp", message, runs::incrementAndGet);
        processedEvents.processOnce("consumidor-amqp", message, runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    // --- Migrations ---

    @Test
    void serviceFlywayAndLibraryTablesCoexistInTheSameSchema() {
        // O contexto subiu com as duas: a V1 do serviço (src/test/resources/db/migration) e a da biblioteca.
        assertThat(jdbc.sql("select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('servico_exemplo', 'fithub_outbox_event', 'fithub_processed_event')")
                .query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    void migrationsRunOnASchemaThatAlreadyHasServiceTablesAndAreIdempotent() {
        jdbc.sql("create schema servico_existente").update();
        jdbc.sql("create table servico_existente.cliente (id uuid primary key)").update();

        FithubCommonsMigrations migrations = new FithubCommonsMigrations(dataSource, "servico_existente");
        assertThat(migrations.migrate()).isEqualTo(1);
        assertThat(migrations.migrate()).isZero();
        assertThat(jdbc.sql("select count(*) from information_schema.tables where table_schema = 'servico_existente' and table_name in ('fithub_outbox_event', 'fithub_processed_event')")
                .query(Integer.class).single()).isEqualTo(2);
    }
}
