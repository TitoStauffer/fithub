package com.fithub.commons.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica no RabbitMQ os eventos pendentes do outbox. Só enxerga linhas já commitadas, então um
 * rollback nunca vira mensagem. Marca como publicado apenas depois da confirmação do broker; a entrega
 * é pelo menos uma vez (por isso o consumo é idempotente). Na primeira falha de um lote, registra o
 * erro e para, preservando a ordem; o próximo ciclo tenta de novo. {@code FOR UPDATE SKIP LOCKED}
 * deixa várias instâncias do serviço publicarem sem duplicar o trabalho.
 */
public class OutboxPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final int batchSize;
    private final Duration pollInterval;
    private final Duration confirmTimeout;
    private ScheduledExecutorService scheduler;

    public OutboxPublisher(JdbcClient jdbc, TransactionTemplate transaction, RabbitTemplate rabbit, int batchSize,
            Duration pollInterval, Duration confirmTimeout) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.batchSize = batchSize;
        this.pollInterval = pollInterval;
        this.confirmTimeout = confirmTimeout;
    }

    private record PendingEvent(UUID id, String exchange, String routingKey, String payload, Timestamp createdAt) {
    }

    /** Publica um lote de pendentes; devolve quantos foram confirmados pelo broker. */
    public int publishPending() {
        Integer published = transaction.execute(status -> {
            List<PendingEvent> pending = jdbc.sql("""
                    select id, exchange, routing_key, payload::text as payload, created_at
                    from fithub_outbox_event
                    where published_at is null
                    order by created_at, id
                    limit ?
                    for update skip locked""")
                    .param(batchSize)
                    .query((rs, row) -> new PendingEvent(rs.getObject("id", UUID.class), rs.getString("exchange"),
                            rs.getString("routing_key"), rs.getString("payload"), rs.getTimestamp("created_at")))
                    .list();
            int count = 0;
            for (PendingEvent event : pending) {
                try {
                    send(event);
                } catch (RuntimeException exception) {
                    log.warn("Falha ao publicar o evento {} do outbox; nova tentativa no próximo ciclo", event.id(), exception);
                    jdbc.sql("update fithub_outbox_event set attempts = attempts + 1, last_error = ? where id = ?")
                            .params(truncate(exception.toString()), event.id())
                            .update();
                    break;
                }
                jdbc.sql("update fithub_outbox_event set published_at = now(), attempts = attempts + 1, last_error = null where id = ?")
                        .param(event.id())
                        .update();
                count++;
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    private void send(PendingEvent event) {
        Message message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.id().toString())
                .setType(event.routingKey())
                .setTimestamp(event.createdAt())
                .setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT)
                .build();
        rabbit.invoke(operations -> {
            operations.send(event.exchange(), event.routingKey(), message);
            operations.waitForConfirmsOrDie(confirmTimeout.toMillis());
            return null;
        });
    }

    private static String truncate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("fithub-outbox-publisher").factory());
        scheduler.scheduleWithFixedDelay(this::publishSafely, pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void publishSafely() {
        try {
            publishPending();
        } catch (RuntimeException exception) {
            log.warn("Ciclo do publicador do outbox falhou; nova tentativa no próximo ciclo", exception);
        }
    }

    @Override
    public synchronized void stop() {
        if (scheduler == null) return;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(confirmTimeout.toMillis() * 2, TimeUnit.MILLISECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException exception) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        scheduler = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return scheduler != null;
    }
}
