package com.fithub.commons.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica no RabbitMQ os eventos pendentes do outbox, na ordem de gravação.
 * <ul>
 * <li>Só enxerga linhas já commitadas: um rollback nunca vira mensagem.</li>
 * <li>Marca como publicado apenas depois da confirmação do broker. A entrega é pelo menos uma vez, por
 * isso o consumo é idempotente.</li>
 * <li><b>Ordem:</b> um único publicador por banco de serviço, garantido pelo lock consultivo
 * {@value #ADVISORY_LOCK_KEY} do Postgres, mesmo com várias instâncias. Quem não obtém o lock sai do
 * ciclo sem fazer nada. Na primeira falha de um lote, registra o erro e para; o próximo ciclo tenta de
 * novo a partir do mesmo evento.</li>
 * <li>Expurga, de tempos em tempos, os eventos já publicados e os registros de consumo mais antigos que
 * a retenção configurada (o payload pode ter dado pessoal).</li>
 * </ul>
 */
public class OutboxPublisher implements SmartLifecycle {

    /** Chave fixa do lock consultivo que serializa o publicador entre instâncias do mesmo serviço. */
    public static final long ADVISORY_LOCK_KEY = 0x6669746875624FL; // "fithubO"

    /**
     * Cabeçalho W3C com o contexto do trace da requisição que gravou o evento (DEV-0109). A observação do
     * listener do consumidor lê este cabeçalho e continua o mesmo trace.
     */
    public static final String TRACE_PARENT_HEADER = "traceparent";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final FithubMessagingProperties.Publisher settings;
    private ScheduledExecutorService scheduler;

    public OutboxPublisher(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            RabbitTemplate rabbit,
            FithubMessagingProperties.Publisher settings) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.settings = settings;
    }

    private record PendingEvent(
            UUID id, String exchange, String routingKey, String payload, Timestamp createdAt, String traceParent) {}

    /** Publica um lote de pendentes; devolve quantos foram confirmados pelo broker. */
    public int publishPending() {
        Integer published = transaction.execute(status -> {
            Boolean locked = jdbc.sql("select pg_try_advisory_xact_lock(?)")
                    .param(ADVISORY_LOCK_KEY)
                    .query(Boolean.class)
                    .single();
            if (!Boolean.TRUE.equals(locked)) return 0;
            List<PendingEvent> pending = jdbc.sql("""
                    select id, exchange, routing_key, payload::text as payload, created_at, trace_parent
                    from fithub_outbox_event
                    where published_at is null
                    order by sequence
                    limit ?""")
                    .param(settings.batchSize())
                    .query((rs, row) -> new PendingEvent(
                            rs.getObject("id", UUID.class),
                            rs.getString("exchange"),
                            rs.getString("routing_key"),
                            rs.getString("payload"),
                            rs.getTimestamp("created_at"),
                            rs.getString("trace_parent")))
                    .list();
            int count = 0;
            for (PendingEvent event : pending) {
                try {
                    send(event);
                } catch (RuntimeException exception) {
                    log.warn(
                            "Falha ao publicar o evento {} do outbox; nova tentativa no próximo ciclo",
                            event.id(),
                            exception);
                    jdbc.sql("update fithub_outbox_event set attempts = attempts + 1, last_error = ? where id = ?")
                            .params(truncate(exception.toString()), event.id())
                            .update();
                    break;
                }
                jdbc.sql(
                                "update fithub_outbox_event set published_at = now(), attempts = attempts + 1, last_error = null where id = ?")
                        .param(event.id())
                        .update();
                count++;
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    /** Remove eventos publicados e registros de consumo além da retenção; devolve quantas linhas saíram. */
    public int purgeExpired() {
        int outbox = jdbc.sql("delete from fithub_outbox_event where published_at < now() - make_interval(secs => ?)")
                .param(settings.outboxRetention().toSeconds())
                .update();
        int processed = jdbc.sql(
                        "delete from fithub_processed_event where processed_at < now() - make_interval(secs => ?)")
                .param(settings.processedRetention().toSeconds())
                .update();
        return outbox + processed;
    }

    private void send(PendingEvent event) {
        Message message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.id().toString())
                .setType(event.routingKey())
                .setTimestamp(event.createdAt())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        if (event.traceParent() != null) {
            message.getMessageProperties().setHeader(TRACE_PARENT_HEADER, event.traceParent());
        }
        rabbit.invoke(operations -> {
            operations.send(event.exchange(), event.routingKey(), message);
            operations.waitForConfirmsOrDie(settings.confirmTimeout().toMillis());
            return null;
        });
    }

    private static String truncate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fithub-outbox-publisher").factory());
        long poll = settings.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(
                () -> safely("publicação", () -> publishPending()), poll, poll, TimeUnit.MILLISECONDS);
        long purge = settings.purgeInterval().toMillis();
        scheduler.scheduleWithFixedDelay(
                () -> safely("expurgo", () -> purgeExpired()), purge, purge, TimeUnit.MILLISECONDS);
    }

    private static void safely(String task, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("Ciclo de {} do outbox falhou; nova tentativa no próximo ciclo", task, exception);
        }
    }

    @Override
    public synchronized void stop() {
        if (scheduler == null) return;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(settings.confirmTimeout().toMillis() * 2, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
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
