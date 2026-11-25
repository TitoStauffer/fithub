package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sem assinatura válida, a nutricionista não cria, edita nem publica plano alimentar (DEV-0101, FR-06), e o que já
 * entregou continua legível. Inclui o consumo do {@code AssinaturaAlterada} pelo contrato.
 */
class SubscriptionBlockApiTest extends ServiceIntegrationTest {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    FoodRepository foods;

    private UUID professional;
    private UUID client;
    private UUID rice;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>} traz o papel de profissional. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!token.startsWith("nutri-")) {
                    throw new BadJwtException("token inválido");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token.substring("nutri-".length()))
                        .claim("realm_access", Map.of("roles", List.of("client", "nutritionist")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
            };
        }
    }

    @BeforeEach
    void setUp() {
        professional = UUID.randomUUID();
        client = UUID.randomUUID();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :professional, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", client)
                .param("professional", professional)
                .update();
        rice = UUID.randomUUID();
        foods.save(new Food(
                rice,
                Food.Kind.TACO,
                null,
                "Arroz, tipo 1, cozido",
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("128"),
                        new BigDecimal("2.5"),
                        new BigDecimal("28.1"),
                        new BigDecimal("0.2"),
                        new BigDecimal("1.6")),
                true,
                new Food.Source("TACO 4ª edição", UUID.randomUUID().toString()),
                Instant.now(),
                Instant.now()));
    }

    private String body() {
        return "{\"name\": \"Plano\", \"startsOn\": \"2026-12-01\", \"meals\": [{\"name\": \"Almoço\","
                + " \"items\": [{\"foodId\": \"" + rice + "\", \"amount\": 100}]}]}";
    }

    private ResultActions createPlan() throws Exception {
        return mvc.perform(post("/clients/" + client + "/meal-plans")
                .header("Authorization", "Bearer nutri-" + professional)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()));
    }

    private String created() throws Exception {
        return createPlan()
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    private void publishEvent(String eventId, String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(
                "fithub.account",
                "account.subscription-changed.v1",
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private static String event(UUID professional, boolean active, String occurredAt, String validUntil) {
        return """
                {"professionalId": "%s", "active": %s, "occurredAt": "%s"%s}
                """.formatted(
                professional, active, occurredAt, validUntil == null ? "" : ", \"validUntil\": \"" + validUntil + "\"");
    }

    private Instant projectedValidUntil(UUID professional) {
        return jdbc.sql("select valid_until from professional_subscription where professional_id = :id")
                .param("id", professional)
                .query(java.sql.Timestamp.class)
                .optional()
                .map(java.sql.Timestamp::toInstant)
                .orElse(null);
    }

    private void awaitValidUntil(UUID professional, Instant expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (expected.equals(projectedValidUntil(professional))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("A projeção não chegou a " + expected);
    }

    private void awaitProcessed(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.sql("select count(*) from fithub_processed_event where event_id = cast(:id as uuid)")
                    .param("id", eventId)
                    .query(Integer.class)
                    .single();
            if (count > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("O evento não foi processado.");
    }

    private void awaitDead(String marker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Message received = rabbit.receive("nutrition.account-events.dlq", 500);
            if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(marker)) {
                return;
            }
        }
        throw new AssertionError("A mensagem inválida não chegou à fila de mensagens mortas.");
    }

    private void project(UUID professional, boolean active, Instant validUntil) {
        jdbc.sql("""
                        insert into professional_subscription (professional_id, active, valid_until, occurred_at)
                        values (:id, :active, :validUntil, now())
                        on conflict (professional_id) do update
                           set active = excluded.active, valid_until = excluded.valid_until,
                               occurred_at = excluded.occurred_at
                        """)
                .param("id", professional)
                .param("active", active)
                .param("validUntil", validUntil == null ? null : java.sql.Timestamp.from(validUntil))
                .update();
    }

    @Test
    void consomeOEventoPeloContratoEOMaisNovoVence() throws Exception {
        UUID professional = UUID.randomUUID();
        String current = event(professional, true, "2026-11-25T12:00:00Z", "2026-12-09T12:00:00Z");
        assertThat(EventContracts.validate(EventContract.SUBSCRIPTION_CHANGED_V1, current))
                .isEmpty();
        publishEvent(UUID.randomUUID().toString(), current);
        awaitValidUntil(professional, Instant.parse("2026-12-09T12:00:00Z"));

        // Um evento mais antigo que chega depois não volta a assinatura para trás.
        String older = event(professional, false, "2026-11-20T12:00:00Z", "2026-11-21T12:00:00Z");
        assertThat(EventContracts.validate(EventContract.SUBSCRIPTION_CHANGED_V1, older))
                .isEmpty();
        String olderId = UUID.randomUUID().toString();
        publishEvent(olderId, older);
        awaitProcessed(olderId);
        assertThat(projectedValidUntil(professional)).isEqualTo(Instant.parse("2026-12-09T12:00:00Z"));

        // Fora do contrato (sem active): vai para a fila de mensagens mortas e não projeta nada.
        UUID other = UUID.randomUUID();
        publishEvent(UUID.randomUUID().toString(), """
                {"professionalId": "%s", "occurredAt": "2026-11-25T12:00:00Z"}
                """.formatted(other));
        awaitDead(other.toString());
        assertThat(projectedValidUntil(other)).isNull();
    }

    @Test
    void comAssinaturaVencidaNaoCriaNemEditaNemPublicaMasContinuaLendo() throws Exception {
        String planPath = created();
        project(professional, false, Instant.now().minus(Duration.ofMinutes(1)));

        createPlan()
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.detail").value("Sua assinatura venceu. Renove para criar ou editar prescrições."));
        mvc.perform(put(planPath)
                        .header("Authorization", "Bearer nutri-" + professional)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
        mvc.perform(post(planPath + "/publish").header("Authorization", "Bearer nutri-" + professional))
                .andExpect(status().isForbidden());
        mvc.perform(get(planPath).header("Authorization", "Bearer nutri-" + professional))
                .andExpect(status().isOk());

        // Renovada (validUntil no futuro), volta a prescrever, mesmo com active falso de um evento anterior.
        project(professional, false, Instant.now().plus(Duration.ofDays(30)));
        createPlan().andExpect(status().isCreated());
    }

    @Test
    void semValidUntilValeOActiveESemEventoNaoBloqueia() throws Exception {
        // Sem evento recebido: não bloqueia.
        createPlan().andExpect(status().isCreated());
        project(professional, false, null);
        createPlan().andExpect(status().isForbidden());
        project(professional, true, null);
        createPlan().andExpect(status().isCreated());
    }
}
