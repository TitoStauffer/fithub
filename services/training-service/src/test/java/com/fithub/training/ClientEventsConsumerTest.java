package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consumidor dos eventos do client-service no training (DEV-0039): projeção de vínculos de personal e de clientes,
 * idempotência, ordem trocada, payload inválido e a verificação de acesso por vínculo.
 */
class ClientEventsConsumerTest extends ServiceIntegrationTest {

    private static final String EXCHANGE = "fithub.client";

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @TestConfiguration
    static class Tokens {

        /** {@code personal-<uuid>} traz o papel de personal; {@code conta-<uuid>}, só o de cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("conta-", List.of("client"), "personal-", List.of("client", "personal"));
            return token -> rolesByPrefix.entrySet().stream()
                    .filter(entry -> token.startsWith(entry.getKey()))
                    .findFirst()
                    .map(entry -> Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject(token.substring(entry.getKey().length()))
                            .claim("realm_access", Map.of("roles", entry.getValue()))
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(300))
                            .build())
                    .orElseThrow(() -> new BadJwtException("token inválido"));
        }
    }

    private void publish(String routingKey, String eventId, String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(EXCHANGE, routingKey, new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private static String linkEvent(UUID linkId, UUID client, UUID professional, String type, String extra) {
        return """
                {"linkId": "%s", "clientId": "%s", "professionalId": "%s", "professionalType": "%s", %s,
                 "occurredAt": "2026-11-23T12:00:00Z"}
                """.formatted(linkId, client, professional, type, extra);
    }

    private void activate(UUID linkId, UUID client, UUID professional, String type, String eventId) {
        String payload = linkEvent(linkId, client, professional, type, "\"consentScope\": [\"profile\"]");
        assertThat(EventContracts.validate(EventContract.LINK_ACTIVATED_V1, payload))
                .isEmpty();
        publish("client.link-activated.v1", eventId, payload);
    }

    private void end(UUID linkId, UUID client, UUID professional, String type, String eventId) {
        String payload = linkEvent(linkId, client, professional, type, "\"endedBy\": \"client\"");
        assertThat(EventContracts.validate(EventContract.LINK_ENDED_V1, payload))
                .isEmpty();
        publish("client.link-ended.v1", eventId, payload);
    }

    private void clientUpdated(UUID client, String name, String occurredAt) {
        String payload = """
                {"clientId": "%s", "name": "%s", "birthDate": "1990-11-24", "gender": "FEMALE", "occurredAt": "%s"}
                """.formatted(client, name, occurredAt);
        assertThat(EventContracts.validate(EventContract.CLIENT_UPDATED_V1, payload))
                .isEmpty();
        publish("client.client-updated.v1", UUID.randomUUID().toString(), payload);
    }

    private boolean processed(String eventId) {
        return jdbc.sql("select count(*) from fithub_processed_event where event_id = :id")
                        .param("id", UUID.fromString(eventId))
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private int view(UUID professional, UUID client) {
        try {
            return mvc.perform(get("/clients/" + client).header("Authorization", "Bearer personal-" + professional))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
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

    private void awaitProcessed(String eventId) throws InterruptedException {
        await(() -> processed(eventId), "evento processado");
    }

    @Test
    void vinculoAtivoDaAcessoAosDadosBasicosDoClienteEOEncerramentoCorta() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        clientUpdated(client, "Ana Cliente", "2026-11-23T10:00:00Z");
        String activation = UUID.randomUUID().toString();
        activate(link, client, personal, "personal_trainer", activation);
        awaitProcessed(activation);
        await(
                () -> jdbc.sql("select count(*) from client where client_id = :id")
                                .param("id", client)
                                .query(Integer.class)
                                .single()
                        == 1,
                "cliente projetado");

        mvc.perform(get("/clients/" + client).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Cliente"))
                .andExpect(jsonPath("$.birthDate").value("1990-11-24"));
        // Outro personal, sem vínculo: 404, como se o cliente não existisse.
        assertThat(view(UUID.randomUUID(), client)).isEqualTo(404);
        mvc.perform(get("/clients/" + client).header("Authorization", "Bearer conta-" + personal))
                .andExpect(status().isForbidden());

        String ending = UUID.randomUUID().toString();
        end(link, client, personal, "personal_trainer", ending);
        awaitProcessed(ending);

        assertThat(view(personal, client)).isEqualTo(404);
    }

    @Test
    void vinculoDeNutricionistaNaoEProjetadoNoTraining() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID nutri = UUID.randomUUID();
        clientUpdated(client, "Ana", "2026-11-23T10:00:00Z");
        String activation = UUID.randomUUID().toString();

        activate(link, client, nutri, "nutritionist", activation);
        awaitProcessed(activation);

        assertThat(jdbc.sql("select count(*) from client_link where link_id = :id")
                        .param("id", link)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(view(nutri, client)).isEqualTo(404);
    }

    @Test
    void reentregaEAtivacaoAtrasadaNaoReativamVinculoEncerrado() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        clientUpdated(client, "Ana", "2026-11-23T10:00:00Z");
        String activation = UUID.randomUUID().toString();
        activate(link, client, personal, "personal_trainer", activation);
        awaitProcessed(activation);
        String ending = UUID.randomUUID().toString();
        end(link, client, personal, "personal_trainer", ending);
        awaitProcessed(ending);

        // Reentrega da mesma ativação (mesmo id) e uma ativação repetida com outro id: nenhuma reativa.
        activate(link, client, personal, "personal_trainer", activation);
        String late = UUID.randomUUID().toString();
        activate(link, client, personal, "personal_trainer", late);
        awaitProcessed(late);

        assertThat(view(personal, client)).isEqualTo(404);
        assertThat(jdbc.sql("select status from client_link where link_id = :id")
                        .param("id", link)
                        .query(String.class)
                        .single())
                .isEqualTo("ENDED");
    }

    @Test
    void encerramentoQueChegaAntesDaAtivacaoGanha() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        clientUpdated(client, "Ana", "2026-11-23T10:00:00Z");
        String ending = UUID.randomUUID().toString();
        end(link, client, personal, "personal_trainer", ending);
        awaitProcessed(ending);

        String activation = UUID.randomUUID().toString();
        activate(link, client, personal, "personal_trainer", activation);
        awaitProcessed(activation);

        assertThat(view(personal, client)).isEqualTo(404);
    }

    @Test
    void clienteAtualizadoAtrasadoNaoDesfazOMaisRecente() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID personal = UUID.randomUUID();
        String activation = UUID.randomUUID().toString();
        activate(link, client, personal, "personal_trainer", activation);
        awaitProcessed(activation);

        clientUpdated(client, "Nome Novo", "2026-11-23T11:00:00Z");
        await(() -> view(personal, client) == 200, "cliente projetado");
        clientUpdated(client, "Nome Antigo", "2026-11-23T09:00:00Z");
        Thread.sleep(1_500);

        mvc.perform(get("/clients/" + client).header("Authorization", "Bearer personal-" + personal))
                .andExpect(jsonPath("$.name").value("Nome Novo"));
    }

    @Test
    void payloadForaDoContratoVaiParaAFilaDeMensagensMortasSemMudarAProjecao() throws Exception {
        UUID link = UUID.randomUUID();
        publish("client.link-activated.v1", UUID.randomUUID().toString(), """
                {"linkId": "%s", "clientId": "nao-e-uuid", "professionalType": "personal_trainer"}
                """.formatted(link));

        Message dead = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (dead == null && System.nanoTime() < deadline) {
            Message received = rabbit.receive("training.client-events.dlq", 500);
            if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(link.toString())) {
                dead = received;
            }
        }
        assertThat(dead).as("mensagem na fila de mensagens mortas").isNotNull();
        assertThat(jdbc.sql("select count(*) from client_link where link_id = :id")
                        .param("id", link)
                        .query(Integer.class)
                        .single())
                .isZero();
    }
}
