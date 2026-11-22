package com.fithub.commons.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.json.JsonMapper;

/** Guardas de entrada da mensageria, sem banco nem broker: falham antes de qualquer I/O. */
class MessagingGuardsTest {

    private final ProcessedEvents processedEvents = new ProcessedEvents(null, null);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void outboxRequiresTheServiceExchange() {
        assertThatThrownBy(() -> new OutboxWriter(null, JsonMapper.builder().build(), " ", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fithub.messaging.exchange");
        assertThatThrownBy(() -> new OutboxWriter(null, JsonMapper.builder().build(), null, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outboxRequiresARoutingKey() {
        OutboxWriter writer = new OutboxWriter(null, JsonMapper.builder().build(), "fithub.teste", Clock.systemUTC());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> writer.append(" ", Map.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("routingKey");
        assertThatThrownBy(() -> writer.append(null, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumerNameAndEventIdAreRequired() {
        assertThatThrownBy(() -> processedEvents.processOnce(" ", UUID.randomUUID(), () -> { }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("consumer");
        assertThatThrownBy(() -> processedEvents.processOnce("consumidor", (UUID) null, () -> { }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId");
    }

    @Test
    void messageWithoutAValidEventIdIsRejectedWithoutRequeue() {
        assertThatThrownBy(() -> processedEvents.processOnce("consumidor", message(null), () -> { }))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> processedEvents.processOnce("consumidor", message("nao-e-uuid"), () -> { }))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static Message message(String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        return new Message("{}".getBytes(), properties);
    }
}
