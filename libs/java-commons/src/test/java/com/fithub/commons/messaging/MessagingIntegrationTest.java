package com.fithub.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fithub.commons.testapp.TestApplication;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Outbox e consumo idempotente contra Postgres e RabbitMQ reais (DEV-0014). */
@Testcontainers
@SpringBootTest(
        classes = {TestApplication.class, MessagingIntegrationTest.TraceListener.class},
        properties = {
            "fithub.messaging.exchange=" + MessagingIntegrationTest.EXCHANGE,
            "fithub.messaging.publisher.poll-interval=200ms",
        })
class MessagingIntegrationTest {

    static final String EXCHANGE = "fithub.teste";
    private static final String ROUTING_KEY = "teste.algo-aconteceu.v1";
    static final String TRACE_ROUTING_KEY = "teste.rastro.v1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.12");

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.3.8-management");

    @Autowired
    private OutboxWriter outbox;

    @Autowired
    private OutboxPublisher publisher;

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

    @Autowired
    private Tracer tracer;

    @Autowired
    private TraceListener traceListener;

    private JdbcClient jdbc;
    private Queue queue;

    @BeforeEach
    void bindQueue() {
        jdbc = JdbcClient.create(dataSource);
        // Fila durável e não exclusiva, como a de um consumidor real: sobrevive à troca de conexão.
        queue = new Queue("teste-" + UUID.randomUUID(), true, false, false);
        rabbitAdmin.declareQueue(queue);
        Binding binding =
                BindingBuilder.bind(queue).to(new TopicExchange(EXCHANGE)).with("teste.#");
        rabbitAdmin.declareBinding(binding);
    }

    @AfterEach
    void deleteQueue() {
        rabbitAdmin.deleteQueue(queue.getName());
    }

    // --- Outbox transacional ---

    @Test
    void eventWrittenInACommittedTransactionIsPublishedWithItsIdAsMessageId() {
        UUID id = transaction.execute(status -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-1")));

        Message message =
                rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(ROUTING_KEY);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"clienteId\": \"c-1\"}");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                                jdbc.sql("select published_at is not null from fithub_outbox_event where id = ?")
                                        .param(id)
                                        .query(Boolean.class)
                                        .single())
                        .isTrue());
    }

    @Test
    void rolledBackTransactionLeavesNoEventAndPublishesNothing() {
        UUID id = transaction.execute(status -> {
            UUID written = outbox.append(ROUTING_KEY, Map.of("clienteId", "c-2"));
            status.setRollbackOnly();
            return written;
        });

        assertThat(rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(2).toMillis()))
                .isNull();
        assertThat(jdbc.sql("select count(*) from fithub_outbox_event where id = ?")
                        .param(id)
                        .query(Integer.class)
                        .single())
                .isZero();
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
        jdbc.sql(
                        "insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at) values (?, 'fithub.inexistente', ?, '{}'::jsonb, now())")
                .params(id, ROUTING_KEY)
                .update();
        try {
            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> assertThat(jdbc.sql("select attempts from fithub_outbox_event where id = ?")
                                    .param(id)
                                    .query(Integer.class)
                                    .single())
                            .isPositive());
            Map<String, Object> row = jdbc.sql("select published_at, last_error from fithub_outbox_event where id = ?")
                    .param(id)
                    .query()
                    .singleRow();
            assertThat(row.get("published_at")).isNull();
            assertThat(row.get("last_error")).isNotNull();
        } finally {
            jdbc.sql("delete from fithub_outbox_event where id = ?").param(id).update();
        }
    }

    @Test
    void eventsArePublishedInTheOrderTheyWereWritten() {
        List<UUID> written = transaction.execute(status -> List.of(
                outbox.append(ROUTING_KEY, Map.of("ordem", 1)),
                outbox.append(ROUTING_KEY, Map.of("ordem", 2)),
                outbox.append(ROUTING_KEY, Map.of("ordem", 3))));

        List<String> received = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Message message = rabbitTemplate.receive(
                    queue.getName(), Duration.ofSeconds(15).toMillis());
            assertThat(message).isNotNull();
            received.add(message.getMessageProperties().getMessageId());
        }
        assertThat(received)
                .containsExactlyElementsOf(written.stream().map(UUID::toString).toList());
    }

    @Test
    void onlyOnePublisherRunsAtATimeAcrossInstances() throws Exception {
        // Outra instância do serviço segurando o lock consultivo do publicador.
        try (Connection otherInstance = dataSource.getConnection();
                Statement statement = otherInstance.createStatement()) {
            otherInstance.setAutoCommit(false);
            statement.execute("select pg_advisory_xact_lock(" + OutboxPublisher.ADVISORY_LOCK_KEY + ")");
            UUID id = transaction.execute(status -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-5")));

            assertThat(publisher.publishPending()).isZero();
            assertThat(rabbitTemplate.receive(
                            queue.getName(), Duration.ofSeconds(2).toMillis()))
                    .isNull();
            assertThat(jdbc.sql("select published_at is null from fithub_outbox_event where id = ?")
                            .param(id)
                            .query(Boolean.class)
                            .single())
                    .isTrue();

            otherInstance.rollback();
            Message message = rabbitTemplate.receive(
                    queue.getName(), Duration.ofSeconds(15).toMillis());
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        }
    }

    @Test
    void purgeRemovesOnlyRowsOlderThanTheRetention() {
        UUID oldEvent = UUID.randomUUID();
        UUID recentEvent = UUID.randomUUID();
        UUID pendingOldEvent = UUID.randomUUID();
        jdbc.sql(
                        "insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at, published_at) values (?, 'x', 'k', '{}'::jsonb, now() - interval '9 days', now() - interval '8 days')")
                .param(oldEvent)
                .update();
        jdbc.sql(
                        "insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at, published_at) values (?, 'x', 'k', '{}'::jsonb, now() - interval '1 day', now() - interval '1 day')")
                .param(recentEvent)
                .update();
        UUID oldProcessed = UUID.randomUUID();
        UUID recentProcessed = UUID.randomUUID();
        jdbc.sql(
                        "insert into fithub_processed_event (consumer, event_id, processed_at) values ('c', ?, now() - interval '31 days')")
                .param(oldProcessed)
                .update();
        jdbc.sql(
                        "insert into fithub_processed_event (consumer, event_id, processed_at) values ('c', ?, now() - interval '29 days')")
                .param(recentProcessed)
                .update();
        try {
            // Pendente antigo nunca é expurgado: ainda não foi entregue.
            jdbc.sql(
                            "insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at) values (?, 'fithub.inexistente', 'k', '{}'::jsonb, now() - interval '30 days')")
                    .param(pendingOldEvent)
                    .update();
            publisher.purgeExpired();

            assertThat(jdbc.sql("select id from fithub_outbox_event where id in (?, ?, ?)")
                            .params(oldEvent, recentEvent, pendingOldEvent)
                            .query(UUID.class)
                            .list())
                    .containsExactlyInAnyOrder(recentEvent, pendingOldEvent);
            assertThat(jdbc.sql("select event_id from fithub_processed_event where event_id in (?, ?)")
                            .params(oldProcessed, recentProcessed)
                            .query(UUID.class)
                            .list())
                    .containsExactly(recentProcessed);
        } finally {
            jdbc.sql("delete from fithub_outbox_event where id in (?, ?)")
                    .params(recentEvent, pendingOldEvent)
                    .update();
        }
    }

    // --- Trace da requisição até o consumidor (DEV-0109) ---

    @Test
    void eventWrittenInsideATraceCarriesTheTraceToTheConsumer() throws Exception {
        Span request = tracer.nextSpan().name("requisicao-de-teste").start();
        UUID id;
        try (Tracer.SpanInScope scope = tracer.withSpan(request)) {
            id = transaction.execute(status -> outbox.append(TRACE_ROUTING_KEY, Map.of("clienteId", "c-6")));
        } finally {
            request.end();
        }

        Message message =
                rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat((String) message.getMessageProperties().getHeader(OutboxPublisher.TRACE_PARENT_HEADER))
                .matches("00-" + request.context().traceId() + "-"
                        + request.context().spanId() + "-[0-9a-f]{2}");
        // O listener do serviço, com a observação ligada pela java-commons, roda no mesmo trace.
        assertThat(traceListener.traceIds.poll(15, TimeUnit.SECONDS))
                .isEqualTo(request.context().traceId());
    }

    @Test
    void eventWrittenWithoutATraceGoesWithoutTheHeader() {
        UUID id = transaction.execute(status -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-7")));

        Message message =
                rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(message.getMessageProperties().getHeaders()).doesNotContainKey(OutboxPublisher.TRACE_PARENT_HEADER);
    }

    /** Consumidor de teste: guarda o traceId em que o listener rodou. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TraceListener {

        final BlockingQueue<String> traceIds = new LinkedBlockingQueue<>();

        @RabbitListener(
                bindings =
                        @QueueBinding(
                                value =
                                        @org.springframework.amqp.rabbit.annotation.Queue(
                                                value = "fithub-teste-rastro",
                                                autoDelete = "true"),
                                exchange = @Exchange(value = EXCHANGE, type = ExchangeTypes.TOPIC),
                                key = TRACE_ROUTING_KEY))
        void onEvent(Message message) {
            traceIds.add(
                    io.opentelemetry.api.trace.Span.current().getSpanContext().getTraceId());
        }
    }

    // --- Consumo idempotente ---

    @Test
    void repeatedEventIsProcessedOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();

        assertThat(processedEvents.processOnce("projecao-vinculos", eventId, runs::incrementAndGet))
                .isTrue();
        assertThat(processedEvents.processOnce("projecao-vinculos", eventId, runs::incrementAndGet))
                .isFalse();
        assertThat(runs).hasValue(1);
        // Outro consumidor do mesmo evento processa a sua parte.
        assertThat(processedEvents.processOnce("outro-consumidor", eventId, runs::incrementAndGet))
                .isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void failedHandlerDoesNotMarkTheEventSoARedeliveryProcessesIt() {
        UUID eventId = UUID.randomUUID();
        assertThatThrownBy(() -> processedEvents.processOnce("projecao-vinculos", eventId, () -> {
                    throw new IllegalStateException("falha no handler");
                }))
                .isInstanceOf(IllegalStateException.class);

        AtomicInteger runs = new AtomicInteger();
        assertThat(processedEvents.processOnce("projecao-vinculos", eventId, runs::incrementAndGet))
                .isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void publishedMessageIsDeduplicatedByItsMessageId() {
        transaction.execute(status -> outbox.append(ROUTING_KEY, Map.of("clienteId", "c-4")));
        Message message =
                rabbitTemplate.receive(queue.getName(), Duration.ofSeconds(15).toMillis());
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
        assertThat(jdbc.sql(
                                "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('servico_exemplo', 'fithub_outbox_event', 'fithub_processed_event')")
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void migrationsRunOnASchemaThatAlreadyHasServiceTablesAndAreIdempotent() {
        jdbc.sql("create schema servico_existente").update();
        jdbc.sql("create table servico_existente.cliente (id uuid primary key)").update();

        FithubCommonsMigrations migrations = new FithubCommonsMigrations(dataSource, "servico_existente");
        assertThat(migrations.migrate()).isEqualTo(2);
        assertThat(migrations.migrate()).isZero();
        assertThat(jdbc.sql(
                                "select count(*) from information_schema.tables where table_schema = 'servico_existente' and table_name in ('fithub_outbox_event', 'fithub_processed_event')")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }
}
