package com.fithub.nutrition.infrastructure.messaging;

import com.fithub.commons.messaging.FithubMessagingProperties;
import com.fithub.commons.messaging.ProcessedEvents;
import com.fithub.nutrition.application.PrescriptionAccess;
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
 * Consumidor do {@code AssinaturaAlterada} do account-service (DEV-0101). Fila durável própria, consumo idempotente
 * pela {@code java-commons} e fila de mensagens mortas para payload fora do contrato. Nada do payload vai para log.
 */
@Configuration(proxyBeanMethods = false)
class AccountEventsListener {

    static final String EXCHANGE = "fithub.account";
    static final String SUBSCRIPTION_CHANGED = "account.subscription-changed.v1";
    static final String QUEUE = "nutrition.account-events";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    private final PrescriptionAccess prescriptionAccess;
    private final ProcessedEvents processedEvents;
    private final JsonMapper jsonMapper;

    AccountEventsListener(
            PrescriptionAccess prescriptionAccess, ProcessedEvents processedEvents, JsonMapper jsonMapper) {
        this.prescriptionAccess = prescriptionAccess;
        this.processedEvents = processedEvents;
        this.jsonMapper = jsonMapper;
    }

    /** AssinaturaAlterada v1, com o {@code validUntil} opcional. */
    record SubscriptionChanged(UUID professionalId, Boolean active, Instant occurredAt, Instant validUntil) {}

    @Bean
    Declarables accountEventsTopology(FithubMessagingProperties messaging) {
        TopicExchange exchange = messaging.sourceExchange(EXCHANGE);
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
        return new Declarables(
                exchange,
                queue,
                QueueBuilder.durable(DEAD_LETTER_QUEUE).build(),
                BindingBuilder.bind(queue).to(exchange).with(SUBSCRIPTION_CHANGED));
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message) {
        if (!SUBSCRIPTION_CHANGED.equals(message.getMessageProperties().getReceivedRoutingKey())) {
            throw new AmqpRejectAndDontRequeueException("Routing key fora do que esta fila consome.");
        }
        SubscriptionChanged event = parse(message);
        processedEvents.processOnce(
                QUEUE,
                message,
                () -> prescriptionAccess.subscriptionChanged(
                        event.professionalId(), event.active(), event.validUntil(), event.occurredAt()));
    }

    private SubscriptionChanged parse(Message message) {
        SubscriptionChanged event;
        try {
            event = jsonMapper.readValue(message.getBody(), SubscriptionChanged.class);
        } catch (JacksonException invalid) {
            throw new AmqpRejectAndDontRequeueException("Payload fora do contrato.");
        }
        if (event.professionalId() == null || event.active() == null || event.occurredAt() == null) {
            throw new AmqpRejectAndDontRequeueException("AssinaturaAlterada fora do contrato.");
        }
        return event;
    }
}
