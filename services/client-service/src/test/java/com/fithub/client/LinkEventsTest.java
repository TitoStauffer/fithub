package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkEvents;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
import tools.jackson.databind.json.JsonMapper;

/** VinculoAtivado e VinculoEncerrado pelo outbox (DEV-0038): transação, contrato, não repetição e RabbitMQ. */
@Import({ClientApiFakes.class, LinkEventsTest.Events.class})
class LinkEventsTest extends ServiceIntegrationTest {

    private static final String ACTIVATED = "client.link-activated.v1";
    private static final String ENDED = "client.link-ended.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

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
        FailingEvents failingLinkEvents(@Qualifier("outboxLinkEvents") LinkEvents outbox) {
            return new FailingEvents(outbox);
        }
    }

    static class FailingEvents implements LinkEvents {

        final AtomicBoolean failAfterWriting = new AtomicBoolean();
        private final LinkEvents outbox;

        FailingEvents(LinkEvents outbox) {
            this.outbox = outbox;
        }

        @Override
        public void activated(Link link, Set<SharedData> consentScope) {
            outbox.activated(link, consentScope);
            failIfAsked();
        }

        @Override
        public void ended(Link link) {
            outbox.ended(link);
            failIfAsked();
        }

        private void failIfAsked() {
            if (failAfterWriting.get()) {
                throw new IllegalStateException("falha depois de gravar o evento");
            }
        }
    }

    @AfterEach
    void stopFailing() {
        events.failAfterWriting.set(false);
    }

    private String client() throws Exception {
        String account = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HEALTH"}
                                """))
                .andExpect(status().isCreated());
        return account;
    }

    private String professional() {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(
                UUID.fromString(account), ProfessionalType.NUTRITIONIST, "Nina", Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    private ResultActions accept(String client, String professional) throws Exception {
        String code = jdbc.sql("select invite_code from professional where id = :id")
                .param("id", UUID.fromString(professional))
                .query(String.class)
                .single();
        return mvc.perform(post("/links")
                .header("Authorization", "Bearer conta-" + client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"consent\": true, \"termsVersion\": \"v1\"}".formatted(code)));
    }

    private String linkOf(String client, String professional) throws Exception {
        return json.readTree(accept(client, professional)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
    }

    private ResultActions end(String token, String linkId) throws Exception {
        return mvc.perform(post("/links/" + linkId + "/end").header("Authorization", "Bearer " + token));
    }

    private List<String> outbox(String routingKey, String clientId) {
        return jdbc.sql("""
                        select payload::text from fithub_outbox_event
                         where routing_key = :routingKey and payload ->> 'clientId' = :clientId
                        """)
                .param("routingKey", routingKey)
                .param("clientId", clientId)
                .query(String.class)
                .list();
    }

    @Test
    void aceiteGravaOVinculoAtivadoPeloContratoComOEscopoDoConsentimento() throws Exception {
        String client = client();
        String nutri = professional();
        String linkId = linkOf(client, nutri);

        List<String> payloads = outbox(ACTIVATED, client);
        assertThat(payloads).hasSize(1);
        String payload = payloads.getFirst();
        assertThat(EventContracts.validate(EventContract.LINK_ACTIVATED_V1, payload))
                .isEmpty();
        assertThat(payload)
                .contains("\"linkId\": \"%s\"".formatted(linkId))
                .contains("\"professionalId\": \"%s\"".formatted(nutri))
                .contains("\"professionalType\": \"nutritionist\"")
                .contains("\"profile\"", "\"health_profile\"", "\"body_assessments\"", "\"photos\"")
                .doesNotContain("Ana")
                .doesNotContain("Nina");
    }

    @Test
    void encerramentoGravaOVinculoEncerradoUmaVezSo() throws Exception {
        String client = client();
        String nutri = professional();
        String linkId = linkOf(client, nutri);

        end("conta-" + client, linkId).andExpect(status().isOk());
        end("conta-" + client, linkId).andExpect(status().isOk());
        end("nutri-" + nutri, linkId).andExpect(status().isOk());

        List<String> payloads = outbox(ENDED, client);
        assertThat(payloads).hasSize(1);
        assertThat(EventContracts.validate(EventContract.LINK_ENDED_V1, payloads.getFirst()))
                .isEmpty();
        assertThat(payloads.getFirst())
                .contains("\"linkId\": \"%s\"".formatted(linkId))
                .contains("\"endedBy\": \"client\"")
                .contains("\"occurredAt\": \"2026-11-23T12:00:00Z\"");
    }

    @Test
    void encerramentosSimultaneosPublicamUmEventoSo() throws Exception {
        for (int round = 0; round < 8; round++) {
            String client = client();
            String nutri = professional();
            String linkId = linkOf(client, nutri);
            List<Callable<Integer>> calls = List.of(
                    () -> end("conta-" + client, linkId)
                            .andReturn()
                            .getResponse()
                            .getStatus(),
                    () -> end("nutri-" + nutri, linkId)
                            .andReturn()
                            .getResponse()
                            .getStatus());

            try (var pool = Executors.newFixedThreadPool(2)) {
                for (var result : pool.invokeAll(calls)) {
                    assertThat(result.get()).isEqualTo(200);
                }
            }

            assertThat(outbox(ENDED, client)).hasSize(1);
        }
    }

    @Test
    void aceiteDesfeitoNaoDeixaVinculoNemEvento() throws Exception {
        String client = client();
        events.failAfterWriting.set(true);

        accept(client, professional()).andExpect(status().isInternalServerError());

        assertThat(jdbc.sql("select count(*) from client_link where client_id = :id")
                        .param("id", UUID.fromString(client))
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(outbox(ACTIVATED, client)).isEmpty();
    }

    @Test
    void encerramentoDesfeitoMantemOVinculoAtivoESemEvento() throws Exception {
        String client = client();
        String linkId = linkOf(client, professional());
        events.failAfterWriting.set(true);

        end("conta-" + client, linkId).andExpect(status().isInternalServerError());

        assertThat(jdbc.sql("select status from client_link where id = :id")
                        .param("id", UUID.fromString(linkId))
                        .query(String.class)
                        .single())
                .isEqualTo("ACTIVE");
        assertThat(outbox(ENDED, client)).isEmpty();
    }

    @Test
    void eventosChegamAoRabbitMqNaExchangeDoServicoComAsRoutingKeysDosContratos() throws Exception {
        Queue queue = new Queue("teste.dev0038." + UUID.randomUUID(), true, false, false);
        amqp.declareQueue(queue);
        TopicExchange exchange = new TopicExchange("fithub.client");
        amqp.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ACTIVATED));
        amqp.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ENDED));
        try {
            String client = client();
            String linkId = linkOf(client, professional());
            end("conta-" + client, linkId).andExpect(status().isOk());

            List<String> keys = new ArrayList<>();
            for (long deadline = System.currentTimeMillis() + 15_000;
                    keys.size() < 2 && System.currentTimeMillis() < deadline; ) {
                Message received = rabbit.receive(queue.getName(), 1_000);
                if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(linkId)) {
                    assertThat(received.getMessageProperties().getReceivedExchange())
                            .isEqualTo("fithub.client");
                    String key = received.getMessageProperties().getReceivedRoutingKey();
                    EventContract contract =
                            key.equals(ACTIVATED) ? EventContract.LINK_ACTIVATED_V1 : EventContract.LINK_ENDED_V1;
                    assertThat(EventContracts.validate(
                                    contract, new String(received.getBody(), StandardCharsets.UTF_8)))
                            .isEmpty();
                    keys.add(key);
                }
            }

            assertThat(keys).containsExactly(ACTIVATED, ENDED);
        } finally {
            amqp.deleteQueue(queue.getName());
        }
    }
}
