package com.fithub.account.infrastructure.messaging;

import com.fithub.account.application.RemoveDeletedLogin;
import com.fithub.commons.messaging.ProcessedEvents;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor do {@code ContaExcluida} do client-service (DEV-0161). Fila durável própria, consumo idempotente pela
 * {@code java-commons} e fila de mensagens mortas para payload fora do contrato. Falha do Keycloak volta para a fila
 * pelas retentativas do listener e, esgotadas, vai para a {@code .dlq}. Nada do payload vai para log.
 */
@Configuration(proxyBeanMethods = false)
class ClientEventsListener {

    static final String EXCHANGE = "fithub.client";
    static final String ACCOUNT_DELETED = "client.account-deleted.v1";
    static final String QUEUE = "account.client-events";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    private final RemoveDeletedLogin removeDeletedLogin;
    private final ProcessedEvents processedEvents;
    private final JsonMapper jsonMapper;

    ClientEventsListener(
            RemoveDeletedLogin removeDeletedLogin, ProcessedEvents processedEvents, JsonMapper jsonMapper) {
        this.removeDeletedLogin = removeDeletedLogin;
        this.processedEvents = processedEvents;
        this.jsonMapper = jsonMapper;
    }

    /** ContaExcluida v1. */
    record AccountDeleted(UUID clientId, Instant occurredAt) {}

    @Bean
    Declarables clientEventsTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
        return new Declarables(
                exchange,
                queue,
                QueueBuilder.durable(DEAD_LETTER_QUEUE).build(),
                BindingBuilder.bind(queue).to(exchange).with(ACCOUNT_DELETED));
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message) {
        if (!ACCOUNT_DELETED.equals(message.getMessageProperties().getReceivedRoutingKey())) {
            throw new AmqpRejectAndDontRequeueException("Routing key fora do que esta fila consome.");
        }
        AccountDeleted event = parse(message);
        processedEvents.processOnce(
                QUEUE, message, () -> removeDeletedLogin.accountDeleted(event.clientId(), event.occurredAt()));
    }

    private AccountDeleted parse(Message message) {
        AccountDeleted event;
        try {
            event = jsonMapper.readValue(message.getBody(), AccountDeleted.class);
        } catch (JacksonException invalid) {
            throw new AmqpRejectAndDontRequeueException("Payload fora do contrato.");
        }
        if (event.clientId() == null || event.occurredAt() == null) {
            throw new AmqpRejectAndDontRequeueException("ContaExcluida fora do contrato.");
        }
        return event;
    }
}
