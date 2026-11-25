package com.fithub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.account.domain.login.LoginAccounts;
import com.fithub.account.domain.professional.ProfessionalRoles;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consumo do {@code ContaExcluida} (DEV-0161, ADR-012): o login sai do Keycloak, exceto quando a conta também é de
 * profissional; reentrega não apaga de novo; falha do Keycloak volta para a fila. O Keycloak é um falso que registra as
 * chamadas.
 */
@Import(AccountDeletedConsumerTest.Fakes.class)
@TestPropertySource(properties = "fithub.professional.role-retry.enabled=false")
class AccountDeletedConsumerTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    FakeLogins logins;

    @TestConfiguration
    static class Fakes {

        /** Token de teste: {@code Bearer conta-<uuid>} vira um JWT com esse {@code sub}; o resto é recusado. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!token.startsWith("conta-")) {
                    throw new BadJwtException("token inválido");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token.substring("conta-".length()))
                        .claim("given_name", "Ana")
                        .claim("family_name", "Profissional")
                        .claim("realm_access", Map.of("roles", List.of("client")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
            };
        }

        @Bean
        @Primary
        ProfessionalRoles fakeRoles() {
            return (accountId, type) -> {};
        }

        @Bean
        @Primary
        FakeLogins fakeLogins() {
            return new FakeLogins();
        }
    }

    /** Registra os logins apagados; falha as primeiras {@code failuresLeft} chamadas. */
    static class FakeLogins implements LoginAccounts {

        final List<UUID> deleted = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger failuresLeft = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void delete(UUID accountId) {
            calls.incrementAndGet();
            if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("Keycloak fora do ar");
            }
            deleted.add(accountId);
        }
    }

    @BeforeEach
    void reset() {
        logins.failuresLeft.set(0);
    }

    private void publish(String eventId, UUID clientId) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        String payload = """
                {"clientId": "%s", "occurredAt": "2026-11-25T12:00:00Z"}
                """.formatted(clientId);
        rabbit.send(
                "fithub.client",
                "client.account-deleted.v1",
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
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

    @Test
    void contaSoDeClienteTemOLoginApagadoUmaVezMesmoComReentrega() throws Exception {
        UUID client = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        publish(eventId, client);
        awaitProcessed(eventId);
        publish(eventId, client);
        String marker = UUID.randomUUID().toString();
        publish(marker, UUID.randomUUID());
        awaitProcessed(marker);

        assertThat(logins.deleted).containsOnlyOnce(client);
    }

    @Test
    void contaQueTambemEDeProfissionalMantemOLogin() throws Exception {
        UUID account = UUID.randomUUID();
        mvc.perform(post("/professionals/me")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "PERSONAL", "registrationNumber": "012345", "registrationCategory": "g",
                                 "registrationState": "sp"}
                                """))
                .andExpect(status().isCreated());
        String eventId = UUID.randomUUID().toString();

        publish(eventId, account);
        awaitProcessed(eventId);

        assertThat(logins.deleted).doesNotContain(account);
    }

    @Test
    void falhaDoKeycloakVoltaParaAFilaEOLoginSaiNaRetentativa() throws Exception {
        UUID client = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        logins.failuresLeft.set(1);

        publish(eventId, client);
        awaitProcessed(eventId);

        assertThat(logins.deleted).containsOnlyOnce(client);
    }
}
