package com.fithub.nutrition;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consumidor dos eventos do client-service no nutrition (DEV-0151): projeção de vínculos de nutricionista e de clientes,
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

        /** {@code nutri-<uuid>} traz o papel de nutricionista; {@code conta-<uuid>}, só o de cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("conta-", List.of("client"), "nutri-", List.of("client", "nutritionist"));
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

    private String clientUpdated(UUID client, String name, String occurredAt) {
        String payload = """
                {"clientId": "%s", "name": "%s", "birthDate": "1990-11-24", "gender": "FEMALE", "occurredAt": "%s"}
                """.formatted(client, name, occurredAt);
        assertThat(EventContracts.validate(EventContract.CLIENT_UPDATED_V1, payload))
                .isEmpty();
        String eventId = UUID.randomUUID().toString();
        publish("client.client-updated.v1", eventId, payload);
        return eventId;
    }

    /** Espera a mensagem com o marcador chegar à fila de mensagens mortas. */
    private void awaitDead(String marker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Message received = rabbit.receive("nutrition.client-events.dlq", 500);
            if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(marker)) {
                return;
            }
        }
        throw new AssertionError("mensagem não chegou à fila de mensagens mortas: " + marker);
    }

    private int count(String table, String column, UUID id) {
        return jdbc.sql("select count(*) from " + table + " where " + column + " = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
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
            return mvc.perform(get("/clients/" + client).header("Authorization", "Bearer nutri-" + professional))
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
        UUID nutri = UUID.randomUUID();
        String activation = UUID.randomUUID().toString();
        activate(link, client, nutri, "nutritionist", activation);
        awaitProcessed(activation);
        // O client-service republica os dados básicos logo depois do VinculoAtivado (DEV-0153).
        clientUpdated(client, "Ana Cliente", "2026-11-23T10:00:00Z");
        await(
                () -> jdbc.sql("select count(*) from client where client_id = :id")
                                .param("id", client)
                                .query(Integer.class)
                                .single()
                        == 1,
                "cliente projetado");

        mvc.perform(get("/clients/" + client).header("Authorization", "Bearer nutri-" + nutri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Cliente"))
                .andExpect(jsonPath("$.birthDate").value("1990-11-24"));
        // Outro nutricionista, sem vínculo: 404, como se o cliente não existisse.
        assertThat(view(UUID.randomUUID(), client)).isEqualTo(404);
        mvc.perform(get("/clients/" + client).header("Authorization", "Bearer conta-" + nutri))
                .andExpect(status().isForbidden());

        String ending = UUID.randomUUID().toString();
        end(link, client, nutri, "nutritionist", ending);
        awaitProcessed(ending);

        assertThat(view(nutri, client)).isEqualTo(404);
    }

    @Test
    void vinculoDePersonalNaoEProjetadoNoNutrition() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID otherType = UUID.randomUUID();
        clientUpdated(client, "Ana", "2026-11-23T10:00:00Z");
        String activation = UUID.randomUUID().toString();

        activate(link, client, otherType, "personal_trainer", activation);
        awaitProcessed(activation);

        assertThat(jdbc.sql("select count(*) from client_link where link_id = :id")
                        .param("id", link)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(view(otherType, client)).isEqualTo(404);
    }

    @Test
    void reentregaEAtivacaoAtrasadaNaoReativamVinculoEncerrado() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID nutri = UUID.randomUUID();
        clientUpdated(client, "Ana", "2026-11-23T10:00:00Z");
        String activation = UUID.randomUUID().toString();
        activate(link, client, nutri, "nutritionist", activation);
        awaitProcessed(activation);
        String ending = UUID.randomUUID().toString();
        end(link, client, nutri, "nutritionist", ending);
        awaitProcessed(ending);

        // Reentrega da mesma ativação (mesmo id) e uma ativação repetida com outro id: nenhuma reativa.
        activate(link, client, nutri, "nutritionist", activation);
        String late = UUID.randomUUID().toString();
        activate(link, client, nutri, "nutritionist", late);
        awaitProcessed(late);

        assertThat(view(nutri, client)).isEqualTo(404);
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
        UUID nutri = UUID.randomUUID();
        clientUpdated(client, "Ana", "2026-11-23T10:00:00Z");
        String ending = UUID.randomUUID().toString();
        end(link, client, nutri, "nutritionist", ending);
        awaitProcessed(ending);

        String activation = UUID.randomUUID().toString();
        activate(link, client, nutri, "nutritionist", activation);
        awaitProcessed(activation);

        assertThat(view(nutri, client)).isEqualTo(404);
    }

    private int copies(UUID client) {
        return jdbc.sql("select count(*) from client where client_id = :id")
                .param("id", client)
                .query(Integer.class)
                .single();
    }

    // Minimização (DEV-0153): sem vínculo ativo neste serviço, os dados básicos do cliente não são guardados.
    @Test
    void clienteSemVinculoAtivoNaoTemCopiaLocal() throws Exception {
        UUID client = UUID.randomUUID();

        awaitProcessed(clientUpdated(client, "Ana", "2026-11-23T10:00:00Z"));

        assertThat(copies(client)).isZero();
    }

    @Test
    void encerramentoDoUltimoVinculoApagaACopiaLocal() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID nutri = UUID.randomUUID();
        String activation = UUID.randomUUID().toString();
        activate(link, client, nutri, "nutritionist", activation);
        awaitProcessed(activation);
        awaitProcessed(clientUpdated(client, "Ana", "2026-11-23T10:00:00Z"));
        assertThat(copies(client)).isEqualTo(1);

        String ending = UUID.randomUUID().toString();
        end(link, client, nutri, "nutritionist", ending);
        awaitProcessed(ending);

        assertThat(copies(client)).isZero();
    }

    // O encerramento de um vínculo antigo pode chegar depois da ativação de outro: a cópia continua.
    @Test
    void encerramentoDeVinculoAntigoNaoApagaACopiaDeQuemTemOutroAtivo() throws Exception {
        UUID oldLink = UUID.randomUUID();
        UUID newLink = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        String first = UUID.randomUUID().toString();
        activate(oldLink, client, UUID.randomUUID(), "nutritionist", first);
        awaitProcessed(first);
        String second = UUID.randomUUID().toString();
        activate(newLink, client, UUID.randomUUID(), "nutritionist", second);
        awaitProcessed(second);
        awaitProcessed(clientUpdated(client, "Ana", "2026-11-23T10:00:00Z"));

        String ending = UUID.randomUUID().toString();
        end(oldLink, client, UUID.randomUUID(), "nutritionist", ending);
        awaitProcessed(ending);

        assertThat(copies(client)).isEqualTo(1);
    }

    // DEV-0165: evento atrasado de quem já excluiu a conta (reprocessado da .dlq, por exemplo) não volta a gravar o id
    // nem o
    // nome do cliente. Um cliente que não excluiu a conta, no mesmo teste, continua sendo projetado.
    // DEV-0165 (Code Review do PR #348): a conta que também é de profissional mantém o id ao excluir o lado cliente
    // (DEV-0161). Se ela volta como cliente, o vínculo aceito depois da exclusão é projetado. O evento do mesmo
    // instante da exclusão (o encerramento dos vínculos, na mesma transação) continua descartado. Os eventos de
    // vínculo do teste ocorrem às 12:00.
    @Test
    void vinculoDeContaQueVoltouDepoisDaExclusaoEProjetado() throws Exception {
        UUID returned = UUID.randomUUID();
        UUID sameInstant = UUID.randomUUID();
        insertDeleted(returned, "2026-11-23T11:00:00Z");
        insertDeleted(sameInstant, "2026-11-23T12:00:00Z");

        String activation = UUID.randomUUID().toString();
        activate(UUID.randomUUID(), returned, UUID.randomUUID(), "nutritionist", activation);
        awaitProcessed(activation);
        awaitProcessed(clientUpdated(returned, "Ana", "2026-11-23T13:00:00Z"));
        String sameActivation = UUID.randomUUID().toString();
        activate(UUID.randomUUID(), sameInstant, UUID.randomUUID(), "nutritionist", sameActivation);
        awaitProcessed(sameActivation);

        assertThat(links(returned)).isEqualTo(1);
        assertThat(copies(returned)).isEqualTo(1);
        assertThat(links(sameInstant)).isZero();
    }

    private void insertDeleted(UUID client, String deletedAt) {
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:c, cast(:at as timestamptz))")
                .param("c", client)
                .param("at", deletedAt)
                .update();
    }

    private int links(UUID client) {
        return jdbc.sql("select count(*) from client_link where client_id = :c")
                .param("c", client)
                .query(Integer.class)
                .single();
    }

    @Test
    void eventoDeClienteQueJaExcluiuAContaNaoVoltaAProjecao() throws Exception {
        UUID deleted = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:c, now())")
                .param("c", deleted)
                .update();
        UUID link = UUID.randomUUID();
        UUID otherLink = UUID.randomUUID();

        String activation = UUID.randomUUID().toString();
        activate(link, deleted, UUID.randomUUID(), "nutritionist", activation);
        awaitProcessed(activation);
        awaitProcessed(clientUpdated(deleted, "Excluída", "2026-11-23T10:00:00Z"));
        String ending = UUID.randomUUID().toString();
        end(UUID.randomUUID(), deleted, UUID.randomUUID(), "nutritionist", ending);
        awaitProcessed(ending);
        String otherActivation = UUID.randomUUID().toString();
        activate(otherLink, other, UUID.randomUUID(), "nutritionist", otherActivation);
        awaitProcessed(otherActivation);
        awaitProcessed(clientUpdated(other, "Ana", "2026-11-23T10:00:00Z"));

        assertThat(jdbc.sql("select count(*) from client_link where client_id = :c")
                        .param("c", deleted)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(copies(deleted)).isZero();
        assertThat(copies(other)).isEqualTo(1);
    }

    @Test
    void clienteAtualizadoAtrasadoNaoDesfazOMaisRecente() throws Exception {
        UUID link = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID nutri = UUID.randomUUID();
        String activation = UUID.randomUUID().toString();
        activate(link, client, nutri, "nutritionist", activation);
        awaitProcessed(activation);

        clientUpdated(client, "Nome Novo", "2026-11-23T11:00:00Z");
        await(() -> view(nutri, client) == 200, "cliente projetado");
        // Só confere o nome depois de o evento atrasado ter sido consumido de fato.
        awaitProcessed(clientUpdated(client, "Nome Antigo", "2026-11-23T09:00:00Z"));

        mvc.perform(get("/clients/" + client).header("Authorization", "Bearer nutri-" + nutri))
                .andExpect(jsonPath("$.name").value("Nome Novo"));
    }

    @Test
    void payloadForaDoContratoVaiParaAFilaDeMensagensMortasSemMudarAProjecao() throws Exception {
        UUID link = UUID.randomUUID();
        publish("client.link-activated.v1", UUID.randomUUID().toString(), """
                {"linkId": "%s", "clientId": "nao-e-uuid", "professionalType": "nutritionist"}
                """.formatted(link));

        awaitDead(link.toString());
        assertThat(count("client_link", "link_id", link)).isZero();
    }

    /** JSON válido que passa do Jackson e só é barrado pelas guardas do consumidor (aprendizado de mensageria). */
    @Test
    void eventoDeVinculoComJsonValidoMasForaDoContratoVaiParaAFilaDeMensagensMortas() {
        UUID client = UUID.randomUUID();
        UUID nutri = UUID.randomUUID();
        Map<String, String> invalid =
                Map.of("tipo de profissional desconhecido", """
                {"linkId": "%s", "clientId": "%s", "professionalId": "%s", "professionalType": "outro",
                 "occurredAt": "2026-11-23T12:00:00Z"}
                """, "sem professionalId", """
                {"linkId": "%s", "clientId": "%s", "professionalType": "nutritionist",
                 "occurredAt": "2026-11-23T12:00:00Z", "ignorado": "%s"}
                """, "sem occurredAt", """
                {"linkId": "%s", "clientId": "%s", "professionalId": "%s", "professionalType": "nutritionist"}
                """);

        invalid.forEach((description, template) -> {
            UUID link = UUID.randomUUID();
            String eventId = UUID.randomUUID().toString();
            publish("client.link-activated.v1", eventId, template.formatted(link, client, nutri));

            awaitDead(link.toString());
            assertThat(count("client_link", "link_id", link)).as(description).isZero();
            // Mensagem rejeitada não conta como processada: descartar em silêncio seria perder o evento.
            assertThat(processed(eventId)).as(description).isFalse();
        });
    }

    @Test
    void clienteAtualizadoComJsonValidoMasForaDoContratoVaiParaAFilaDeMensagensMortas() {
        Map<String, String> invalid = Map.of("nome em branco", """
                {"clientId": "%s", "name": "  ", "birthDate": null, "gender": null,
                 "occurredAt": "2026-11-23T12:00:00Z"}
                """, "sem nome", """
                {"clientId": "%s", "birthDate": null, "gender": null, "occurredAt": "2026-11-23T12:00:00Z"}
                """, "sem occurredAt", """
                {"clientId": "%s", "name": "Ana Cliente", "birthDate": null, "gender": null}
                """);

        invalid.forEach((description, template) -> {
            UUID client = UUID.randomUUID();
            String eventId = UUID.randomUUID().toString();
            publish("client.client-updated.v1", eventId, template.formatted(client));

            awaitDead(client.toString());
            assertThat(count("client", "client_id", client)).as(description).isZero();
            assertThat(processed(eventId)).as(description).isFalse();
        });
    }

    /**
     * O payload rejeitado leva dado pessoal (nome, nascimento, gênero). Nem o serviço nem o Spring AMQP, ao
     * esgotar as tentativas e mandar a mensagem para a fila de mensagens mortas, podem escrevê-lo no log.
     */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void payloadRejeitadoNaoApareceNoLog(CapturedOutput output) {
        UUID client = UUID.randomUUID();
        String marker = "Nome-Marcador-" + UUID.randomUUID();
        publish("client.client-updated.v1", UUID.randomUUID().toString(), """
                {"clientId": "%s", "name": "%s", "birthDate": "1990-11-24", "gender": "FEMALE"}
                """.formatted(client, marker));

        awaitDead(client.toString());

        // O aviso do Spring AMQP precisa ter sido capturado: sem ele, a ausência do marcador não provaria nada.
        assertThat(output.getAll()).contains("Retries exhausted for message");
        assertThat(output.getAll()).doesNotContain(marker).doesNotContain("1990-11-24");
    }

    @Test
    void contaComSubForaDoFormatoNaoTemAcessoENaoViraErro() throws Exception {
        mvc.perform(get("/clients/" + UUID.randomUUID()).header("Authorization", "Bearer nutri-nao-e-uuid"))
                .andExpect(status().isNotFound());
    }
}
