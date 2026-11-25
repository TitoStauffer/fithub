package com.fithub.training.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guardas de entrada do {@code ContaExcluida} no consumidor dos eventos do client-service (DEV-0162), sem banco nem
 * broker. Cada caso usa JSON válido que só a guarda recusa: sem ela, a mensagem chegaria ao consumo idempotente (aqui
 * ausente) e falharia com outra exceção, voltando à fila em vez de ir direto para a de mensagens mortas.
 */
class ClientEventsListenerGuardsTest {

    private final ClientEventsListener listener =
            new ClientEventsListener(null, null, null, JsonMapper.builder().build());

    private static Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(UUID.randomUUID().toString());
        properties.setReceivedRoutingKey(ClientEventsListener.ACCOUNT_DELETED);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    private void assertRejected(String payload) {
        assertThatThrownBy(() -> listener.onMessage(message(payload)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void contaExcluidaSemCampoObrigatorioERecusadaSemReentrega() {
        assertRejected("{\"occurredAt\": \"2026-11-25T12:00:00Z\"}");
        assertRejected("{\"clientId\": \"" + UUID.randomUUID() + "\"}");
        assertRejected("não é JSON");
    }
}
