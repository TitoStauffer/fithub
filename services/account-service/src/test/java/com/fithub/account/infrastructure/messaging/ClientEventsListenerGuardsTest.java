package com.fithub.account.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guardas de entrada do consumidor do {@code ContaExcluida} (DEV-0161), sem banco nem broker. Cada caso usa JSON
 * válido que só a guarda recusa: sem ela, a mensagem chegaria ao consumo idempotente (aqui ausente) e falharia com
 * outra exceção, voltando à fila em vez de ir direto para a de mensagens mortas (aprendizado de mensageria).
 */
class ClientEventsListenerGuardsTest {

    private final ClientEventsListener listener =
            new ClientEventsListener(null, null, JsonMapper.builder().build());

    private static Message message(String routingKey, String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(UUID.randomUUID().toString());
        properties.setReceivedRoutingKey(routingKey);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    private void assertRejected(String routingKey, String payload) {
        assertThatThrownBy(() -> listener.onMessage(message(routingKey, payload)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void routingKeyDeOutroEventoERecusadaSemReentrega() {
        assertRejected(
                "client.client-updated.v1",
                "{\"clientId\": \"" + UUID.randomUUID() + "\", \"occurredAt\": \"2026-11-25T12:00:00Z\"}");
    }

    @Test
    void campoObrigatorioAusenteERecusadoSemReentrega() {
        String key = ClientEventsListener.ACCOUNT_DELETED;
        assertRejected(key, "{\"occurredAt\": \"2026-11-25T12:00:00Z\"}");
        assertRejected(key, "{\"clientId\": \"" + UUID.randomUUID() + "\"}");
        assertRejected(key, "não é JSON");
    }
}
