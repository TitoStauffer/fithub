package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.domain.profile.ClientEvents;
import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** {@code ClienteAtualizado} pelo outbox (DEV-0034): transação, contrato, filtro de mudança e RabbitMQ. */
@Import({ClientApiFakes.class, ClientUpdatedEventTest.Events.class})
class ClientUpdatedEventTest extends ServiceIntegrationTest {

    private static final String ROUTING_KEY = "client.client-updated.v1";

    private static final String BODY = """
            {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
             "goal": "HYPERTROPHY"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FailingEvents events;

    @Autowired
    AmqpAdmin amqp;

    @Autowired
    RabbitTemplate rabbit;

    @TestConfiguration
    static class Events {

        /** Grava pelo outbox de verdade e, se pedido, falha logo depois, dentro da transação. */
        @Bean
        @Primary
        FailingEvents failingEvents(@Qualifier("outboxClientEvents") ClientEvents outbox) {
            return new FailingEvents(outbox);
        }
    }

    static class FailingEvents implements ClientEvents {

        final AtomicBoolean failAfterWriting = new AtomicBoolean();
        private final ClientEvents outbox;

        FailingEvents(ClientEvents outbox) {
            this.outbox = outbox;
        }

        @Override
        public void updated(ClientProfile profile) {
            outbox.updated(profile);
            if (failAfterWriting.get()) {
                throw new IllegalStateException("falha depois de gravar o evento");
            }
        }

        @Override
        public void deleted(UUID clientId, Instant at) {
            outbox.deleted(clientId, at);
        }
    }

    @AfterEach
    void stopFailing() {
        events.failAfterWriting.set(false);
    }

    private ResultActions save(String account, String body) throws Exception {
        return mvc.perform(put("/clients/me/profile")
                .header("Authorization", "Bearer conta-" + account)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<String> outboxPayloads(String account) {
        return jdbc.sql("""
                        select payload::text from fithub_outbox_event
                         where routing_key = :routingKey and payload ->> 'clientId' = :account
                         order by created_at, id
                        """)
                .param("routingKey", ROUTING_KEY)
                .param("account", account)
                .query(String.class)
                .list();
    }

    @Test
    void criarOPerfilGravaOEventoPeloContratoSemSaudeObjetivoNemAltura() throws Exception {
        String account = UUID.randomUUID().toString();

        save(account, BODY).andExpect(status().isCreated());

        List<String> payloads = outboxPayloads(account);
        assertThat(payloads).hasSize(1);
        String payload = payloads.getFirst();
        assertThat(EventContracts.validate(EventContract.CLIENT_UPDATED_V1, payload))
                .isEmpty();
        assertThat(payload)
                .contains("\"name\": \"Ana Cliente\"")
                .contains("\"birthDate\": \"1990-11-24\"")
                .contains("\"gender\": \"FEMALE\"")
                .contains("\"occurredAt\": \"2026-11-23T12:00:00Z\"")
                .doesNotContain("HYPERTROPHY")
                .doesNotContain("165");
    }

    @Test
    void mudarSoAlturaEObjetivoNaoPublicaEvento() throws Exception {
        String account = UUID.randomUUID().toString();
        save(account, BODY).andExpect(status().isCreated());

        save(account, BODY.replace("165", "170").replace("HYPERTROPHY", "HEALTH"))
                .andExpect(status().isOk());
        save(account, BODY).andExpect(status().isOk());

        assertThat(outboxPayloads(account)).hasSize(1);
    }

    @Test
    void mudarNomeNascimentoOuGeneroPublicaOEstadoCompleto() throws Exception {
        String account = UUID.randomUUID().toString();
        save(account, BODY).andExpect(status().isCreated());

        save(account, BODY.replace("Ana Cliente", "Ana Souza")).andExpect(status().isOk());
        save(account, BODY.replace("Ana Cliente", "Ana Souza").replace("1990-11-24", "1990-11-25"))
                .andExpect(status().isOk());
        save(
                        account,
                        BODY.replace("Ana Cliente", "Ana Souza")
                                .replace("1990-11-24", "1990-11-25")
                                .replace("FEMALE", "PREFER_NOT_TO_SAY"))
                .andExpect(status().isOk());

        List<String> payloads = outboxPayloads(account);
        assertThat(payloads).hasSize(4);
        assertThat(payloads.get(1)).contains("\"name\": \"Ana Souza\"").contains("\"birthDate\": \"1990-11-24\"");
        assertThat(payloads.get(2)).contains("\"birthDate\": \"1990-11-25\"").contains("\"gender\": \"FEMALE\"");
        assertThat(payloads.get(3)).contains("\"gender\": \"PREFER_NOT_TO_SAY\"");
        assertThat(payloads)
                .allSatisfy(payload -> assertThat(EventContracts.validate(EventContract.CLIENT_UPDATED_V1, payload))
                        .isEmpty());
    }

    @Test
    void gravacaoDesfeitaNaoDeixaNemOPerfilNemOEvento() throws Exception {
        String account = UUID.randomUUID().toString();
        events.failAfterWriting.set(true);

        save(account, BODY).andExpect(status().isInternalServerError());

        assertThat(jdbc.sql("select count(*) from client_profile where account_id = :account")
                        .param("account", account)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(outboxPayloads(account)).isEmpty();
    }

    @Test
    void atualizacaoDesfeitaMantemOPerfilAnteriorESemEventoNovo() throws Exception {
        String account = UUID.randomUUID().toString();
        save(account, BODY).andExpect(status().isCreated());
        events.failAfterWriting.set(true);

        save(account, BODY.replace("Ana Cliente", "Ana Souza")).andExpect(status().isInternalServerError());

        assertThat(jdbc.sql("select name from client_profile where account_id = :account")
                        .param("account", account)
                        .query(String.class)
                        .single())
                .isEqualTo("Ana Cliente");
        assertThat(outboxPayloads(account)).hasSize(1);
    }

    @Test
    void eventoChegaAoRabbitMqNaExchangeDoServicoComARoutingKeyDoContrato() throws Exception {
        // Fila só do teste, ligada à exchange do client-service antes da gravação (o broker descarta
        // mensagem sem fila ligada). Durável porque o RabbitMQ 4 não aceita fila transitória compartilhada;
        // apagada no fim.
        Queue queue = new Queue("teste.dev0034." + UUID.randomUUID(), true, false, false);
        amqp.declareQueue(queue);
        amqp.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange("fithub.client"))
                .with(ROUTING_KEY));
        String account = UUID.randomUUID().toString();
        try {
            save(account, BODY).andExpect(status().isCreated());

            Message message = null;
            for (long deadline = System.currentTimeMillis() + 15_000; System.currentTimeMillis() < deadline; ) {
                Message received = rabbit.receive(queue.getName(), 1_000);
                if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(account)) {
                    message = received;
                    break;
                }
            }

            assertThat(message)
                    .as("evento publicado na fila ligada à exchange fithub.client")
                    .isNotNull();
            assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(ROUTING_KEY);
            assertThat(message.getMessageProperties().getReceivedExchange()).isEqualTo("fithub.client");
            assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
            assertThat(EventContracts.validate(
                            EventContract.CLIENT_UPDATED_V1, new String(message.getBody(), StandardCharsets.UTF_8)))
                    .isEmpty();
        } finally {
            amqp.deleteQueue(queue.getName());
        }
    }
}
