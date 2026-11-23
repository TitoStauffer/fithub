package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Consumidor do {@code ProfissionalCadastrado} (DEV-0035): projeção, idempotência e fila de mensagens mortas. */
@Import(ClientApiFakes.class)
class ProfessionalRegisteredConsumerTest extends ServiceIntegrationTest {

    private static final String EXCHANGE = "fithub.account";
    private static final String ROUTING_KEY = "account.professional-registered.v1";

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcClient jdbc;

    private void publish(String eventId, String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(EXCHANGE, ROUTING_KEY, new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private static String payload(UUID professionalId, String type, String name) {
        return """
                {"professionalId": "%s", "professionalType": "%s", "name": "%s",
                 "occurredAt": "2026-11-23T10:00:00Z"}
                """.formatted(professionalId, type, name);
    }

    private int professionals(UUID id) {
        return jdbc.sql("select count(*) from professional where id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    private static void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("não aconteceu a tempo: " + description);
    }

    @Test
    void eventoCriaOProfissionalNaProjecaoComCodigoPessoal() throws Exception {
        UUID professionalId = UUID.randomUUID();

        publish(UUID.randomUUID().toString(), payload(professionalId, "nutritionist", "Nina Nutri"));

        await(() -> professionals(professionalId) == 1, "profissional na projeção");
        var row = jdbc.sql("select professional_type, name, invite_code from professional where id = :id")
                .param("id", professionalId)
                .query((rs, index) -> new String[] {
                    rs.getString("professional_type"), rs.getString("name"), rs.getString("invite_code")
                })
                .single();
        assertThat(row[0]).isEqualTo("NUTRITIONIST");
        assertThat(row[1]).isEqualTo("Nina Nutri");
        assertThat(row[2]).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void reentregaDoMesmoEventoNaoDuplicaNemTrocaOCodigo() throws Exception {
        UUID professionalId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        publish(eventId, payload(professionalId, "personal_trainer", "Paula Personal"));
        await(() -> professionals(professionalId) == 1, "primeira entrega");
        String code = jdbc.sql("select invite_code from professional where id = :id")
                .param("id", professionalId)
                .query(String.class)
                .single();

        // Reentrega com o mesmo id de evento, e um nome diferente para provar que o handler não rodou de novo.
        publish(eventId, payload(professionalId, "personal_trainer", "Outro Nome"));
        await(
                () -> jdbc.sql("select count(*) from fithub_processed_event where event_id = :id")
                                .param("id", UUID.fromString(eventId))
                                .query(Integer.class)
                                .single()
                        == 1,
                "evento registrado como processado");
        Thread.sleep(1_500);

        assertThat(professionals(professionalId)).isEqualTo(1);
        assertThat(jdbc.sql("select name || ':' || invite_code from professional where id = :id")
                        .param("id", professionalId)
                        .query(String.class)
                        .single())
                .isEqualTo("Paula Personal:" + code);
    }

    @Test
    void eventoForaDoContratoVaiParaAFilaDeMensagensMortas() throws Exception {
        String marker = UUID.randomUUID().toString();

        publish(UUID.randomUUID().toString(), """
                {"professionalId": "%s", "professionalType": "fisioterapeuta", "name": "X",
                 "occurredAt": "2026-11-23T10:00:00Z"}
                """.formatted(marker));

        Message dead = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (dead == null && System.nanoTime() < deadline) {
            Message received = rabbit.receive("client.professional-registered.dlq", 500);
            if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(marker)) {
                dead = received;
            }
        }
        assertThat(dead).as("mensagem na fila de mensagens mortas").isNotNull();
        assertThat(professionals(UUID.fromString(marker))).isZero();
    }
}
