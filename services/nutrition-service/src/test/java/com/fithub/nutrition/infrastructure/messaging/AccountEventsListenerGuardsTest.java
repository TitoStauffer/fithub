package com.fithub.nutrition.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guardas de entrada do consumidor do {@code AssinaturaAlterada} (DEV-0101), sem banco nem broker. Cada caso usa JSON
 * válido que só a guarda recusa: sem ela, a mensagem chegaria ao consumo idempotente (aqui ausente) e falharia com
 * outra exceção, voltando à fila em vez de ir direto para a de mensagens mortas (Code Review do PR #286).
 */
class AccountEventsListenerGuardsTest {

    private final AccountEventsListener listener =
            new AccountEventsListener(null, null, JsonMapper.builder().build());

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

    private static final String VALID = """
            {"professionalId": "%s", "active": true, "occurredAt": "2026-11-25T12:00:00Z"}
            """.formatted(UUID.randomUUID());

    @Test
    void routingKeyDeOutroEventoERecusadaSemReentrega() {
        assertRejected("account.professional-registered.v1", VALID);
    }

    @Test
    void campoObrigatorioAusenteERecusadoSemReentrega() {
        String key = AccountEventsListener.SUBSCRIPTION_CHANGED;
        assertRejected(key, "{\"active\": true, \"occurredAt\": \"2026-11-25T12:00:00Z\"}");
        assertRejected(
                key, "{\"professionalId\": \"" + UUID.randomUUID() + "\", \"occurredAt\": \"2026-11-25T12:00:00Z\"}");
        assertRejected(key, "{\"professionalId\": \"" + UUID.randomUUID() + "\", \"active\": false}");
        assertRejected(key, "não é JSON");
    }
}
