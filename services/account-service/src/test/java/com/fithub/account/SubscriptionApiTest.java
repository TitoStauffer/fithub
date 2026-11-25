package com.fithub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalRoles;
import com.fithub.testing.service.ServiceIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A assinatura do profissional ponta a ponta (DEV-0099): nasce em teste no cadastro e o estado sai das datas. */
@TestPropertySource(properties = {"fithub.professional.role-retry.enabled=false", "fithub.subscription.trial-days=10"})
class SubscriptionApiTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

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

        /** Keycloak falso: a atribuição do papel não importa aqui. */
        @Bean
        @Primary
        ProfessionalRoles fakeRoles() {
            return (String accountId, Professional.Type type) -> {};
        }
    }

    private String registered() throws Exception {
        String account = UUID.randomUUID().toString();
        mvc.perform(post("/professionals/me")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "NUTRITIONIST", "registrationNumber": "1234", "registrationState": "SP"}
                                """))
                .andExpect(status().isCreated());
        return account;
    }

    private void setDates(String account, Instant trialEndsAt, Instant paidUntil) {
        jdbc.sql("""
                        update subscription
                           set trial_ends_at = :trial, paid_until = :paid, created_at = :trial - interval '20 days'
                         where professional_id = :id
                        """)
                .param("trial", OffsetDateTime.ofInstant(trialEndsAt, java.time.ZoneOffset.UTC))
                .param("paid", paidUntil == null ? null : OffsetDateTime.ofInstant(paidUntil, java.time.ZoneOffset.UTC))
                .param("id", UUID.fromString(account))
                .update();
    }

    @Test
    void cadastroNasceEmTesteComOsDiasConfigurados() throws Exception {
        String account = registered();
        Instant createdAt = jdbc.sql("select created_at from professional where account_id = :a")
                .param("a", account)
                .query(java.sql.Timestamp.class)
                .single()
                .toInstant();

        String body = mvc.perform(
                        get("/professionals/me/subscription").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PROFESSIONAL_MONTHLY"))
                .andExpect(jsonPath("$.status").value("TRIAL"))
                // Nulo, não ausente: o campo vem no corpo (Code Review do PR #285).
                .andExpect(jsonPath("$.paidUntil").value(Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Instant trialEndsAt = Instant.parse(JsonPath.read(body, "$.trialEndsAt"));
        assertThat(Duration.between(createdAt, trialEndsAt)).isEqualTo(Duration.ofDays(10));
        assertThat(Instant.parse(JsonPath.read(body, "$.validUntil"))).isEqualTo(trialEndsAt);
    }

    @Test
    void oEstadoSegueAsDatas() throws Exception {
        String account = registered();
        Instant now = Instant.now();

        setDates(account, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(20)));
        mvc.perform(get("/professionals/me/subscription").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        setDates(account, now.minus(Duration.ofDays(1)), now.minus(Duration.ofHours(1)));
        mvc.perform(get("/professionals/me/subscription").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        setDates(account, now.plus(Duration.ofDays(3)), now.minus(Duration.ofDays(1)));
        mvc.perform(get("/professionals/me/subscription").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"));
    }

    @Test
    void bancoRecusaPlanoForaDaListaETesteQueNaoPassaDoCadastroMesmoPorEscritaDireta() throws Exception {
        UUID professional = UUID.fromString(registered());
        jdbc.sql("delete from subscription where professional_id = :id")
                .param("id", professional)
                .update();
        String insert = """
                insert into subscription (professional_id, plan, trial_ends_at, paid_until, created_at)
                values (:id, :plan, now() + make_interval(days => :days), null, now())
                """;
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", professional)
                        .param("plan", "PROFESSIONAL_YEARLY")
                        .param("days", 14)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_subscription_plan");
        // O fim do teste precisa ser depois do cadastro: no mesmo instante, recusa.
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", professional)
                        .param("plan", "PROFESSIONAL_MONTHLY")
                        .param("days", 0)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_subscription_trial");
        jdbc.sql(insert)
                .param("id", professional)
                .param("plan", "PROFESSIONAL_MONTHLY")
                .param("days", 1)
                .update();
    }

    @Test
    void semCadastroDeProfissional404ESemToken401() throws Exception {
        mvc.perform(get("/professionals/me/subscription").header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Esta conta ainda não tem cadastro de profissional."));
        mvc.perform(get("/professionals/me/subscription")).andExpect(status().isUnauthorized());
    }
}
