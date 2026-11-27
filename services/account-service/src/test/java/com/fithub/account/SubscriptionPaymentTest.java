package com.fithub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.account.domain.payment.PaymentGateway;
import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalRoles;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pagamento da assinatura pelo checkout do Asaas (DEV-0100), ponta a ponta com o Asaas simulado: o checkout com o
 * preço certo, o webhook autenticado e idempotente, o período pago estendido e o fim do preço de lançamento.
 */
@TestPropertySource(
        properties = {
            "fithub.professional.role-retry.enabled=false",
            "fithub.payment.asaas.webhook-token=" + SubscriptionPaymentTest.TOKEN,
            "fithub.subscription.price.launch-until=2999-12-31"
        })
class SubscriptionPaymentTest extends ServiceIntegrationTest {

    static final String TOKEN = "token-de-teste-do-webhook-com-mais-de-32-caracteres";
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FakeAsaas asaas;

    @TestConfiguration
    static class Fakes {

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
            return (String accountId, Professional.Type type) -> {};
        }

        @Bean
        @Primary
        FakeAsaas fakeAsaas() {
            return new FakeAsaas();
        }
    }

    /** Asaas simulado: guarda os checkouts e as trocas de valor, e responde o checkoutSession das assinaturas. */
    static class FakeAsaas implements PaymentGateway {
        final List<Map<String, Object>> checkouts = new CopyOnWriteArrayList<>();
        final List<Map<String, Object>> valueChanges = new CopyOnWriteArrayList<>();
        final Map<String, String> checkoutOfSubscription = new ConcurrentHashMap<>();
        volatile Duration createDelay = Duration.ZERO;

        private static void sleep(Duration delay) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public Checkout createCheckout(UUID professionalId, BigDecimal value, LocalDate firstDueDate) {
            // Demora como a API real, para dois pedidos simultâneos se cruzarem antes de gravar o checkout.
            sleep(createDelay);
            String id = "chk_" + UUID.randomUUID();
            checkouts.add(Map.of("id", id, "professional", professionalId, "value", value, "due", firstDueDate));
            return new Checkout(id, "https://sandbox.asaas.com/checkoutSession/show?id=" + id);
        }

        @Override
        public Optional<String> checkoutOf(String subscriptionId) {
            return Optional.ofNullable(checkoutOfSubscription.get(subscriptionId));
        }

        @Override
        public void changeValue(String subscriptionId, BigDecimal value) {
            valueChanges.add(Map.of("subscription", subscriptionId, "value", value));
        }
    }

    @BeforeEach
    void reset() {
        asaas.checkouts.clear();
        asaas.valueChanges.clear();
        asaas.createDelay = Duration.ZERO;
    }

    // --- Checkout ---

    @Test
    void checkoutIsCreatedWithTheLaunchPriceAndTheFirstChargeAtTheEndOfTheTrial() throws Exception {
        String account = registered();

        checkout(account)
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.checkoutUrl").value(org.hamcrest.Matchers.startsWith("https://sandbox.asaas.com/")))
                .andExpect(jsonPath("$.value").value(150.0))
                .andExpect(jsonPath("$.launchPrice").value(true));

        Map<String, Object> created = asaas.checkouts.getFirst();
        assertThat(created.get("professional")).isEqualTo(UUID.fromString(account));
        assertThat((BigDecimal) created.get("value")).isEqualByComparingTo("150.00");
        LocalDate trialEnd = LocalDate.ofInstant(trialEndsAt(account), ZONE);
        assertThat(created.get("due")).isEqualTo(trialEnd);
        assertThat(jdbc.sql("select launch from payment_checkout where id = ?")
                        .param(created.get("id"))
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void afterTheTrialTheFirstChargeIsToday() throws Exception {
        String account = registered();
        jdbc.sql(
                        "update subscription set trial_ends_at = now() - interval '1 day', created_at = now() - interval '20 days'"
                                + " where professional_id = ?")
                .param(UUID.fromString(account))
                .update();

        checkout(account).andExpect(status().isCreated());

        assertThat(asaas.checkouts.getFirst().get("due")).isEqualTo(LocalDate.now(ZONE));
    }

    @Test
    void anActiveSubscriptionDoesNotGetANewCheckout() throws Exception {
        String account = registered();
        jdbc.sql("update subscription set paid_until = now() + interval '10 days' where professional_id = ?")
                .param(UUID.fromString(account))
                .update();

        checkout(account).andExpect(status().isConflict());

        assertThat(asaas.checkouts).isEmpty();
    }

    @Test
    void anAccountWithoutProfessionalRegistrationGets404() throws Exception {
        checkout(UUID.randomUUID().toString()).andExpect(status().isNotFound());

        assertThat(asaas.checkouts).isEmpty();
    }

    // --- Webhook ---

    @Test
    void theWebhookWithoutTheRightTokenIsRefusedWithoutEffect() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        String event = "evt_" + UUID.randomUUID();
        String body = paymentEvent(event, "PAYMENT_CONFIRMED", "pay_x", subscription, "2026-12-10");

        mvc.perform(post("/webhooks/asaas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/asaas")
                        .header("asaas-access-token", TOKEN + "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(paidUntil(account)).isNull();
        // O banco é compartilhado com as outras classes de teste: confere só esta cobrança.
        assertThat(jdbc.sql("select count(*) from subscription_payment where payment_id = 'pay_x'")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void aConfirmedPaymentExtendsThePaidPeriodToTheNextDueDateOnceEvenIfDeliveredAgain() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        String event = "evt_" + UUID.randomUUID();
        String body = paymentEvent(event, "PAYMENT_CONFIRMED", "pay_" + UUID.randomUUID(), subscription, "2026-12-10");
        long eventsBefore = subscriptionEvents(account);

        webhook(body).andExpect(status().isOk());
        webhook(body).andExpect(status().isOk());

        // Paga o ciclo de 10/12 até o vencimento seguinte, 10/01, com 1 dia de folga para a próxima cobrança.
        Instant expected = LocalDate.of(2027, 1, 11).atStartOfDay(ZONE).toInstant();
        assertThat(paidUntil(account)).isEqualTo(expected);
        assertThat(subscriptionEvents(account) - eventsBefore).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from subscription_payment where subscription_id = ?")
                        .param(subscription)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void aReceivedPaymentWithoutTheConfirmedEventAlsoExtendsThePaidPeriod() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);

        webhook(paymentEvent(
                        "evt_" + UUID.randomUUID(),
                        "PAYMENT_RECEIVED",
                        "pay_" + UUID.randomUUID(),
                        subscription,
                        "2026-12-10"))
                .andExpect(status().isOk());

        assertThat(paidUntil(account))
                .isEqualTo(LocalDate.of(2027, 1, 11).atStartOfDay(ZONE).toInstant());
    }

    @Test
    void theReceivedEventOfAnAlreadyConfirmedPaymentChangesNothing() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        String payment = "pay_" + UUID.randomUUID();
        webhook(paymentEvent("evt_" + UUID.randomUUID(), "PAYMENT_CONFIRMED", payment, subscription, "2026-12-10"));
        long eventsBefore = subscriptionEvents(account);

        webhook(paymentEvent("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", payment, subscription, "2026-12-10"))
                .andExpect(status().isOk());

        assertThat(subscriptionEvents(account)).isEqualTo(eventsBefore);
    }

    @Test
    void anOlderPaymentArrivingLaterNeverShortensThePaidPeriod() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        webhook(paymentEvent(
                "evt_" + UUID.randomUUID(),
                "PAYMENT_CONFIRMED",
                "pay_" + UUID.randomUUID(),
                subscription,
                "2027-01-10"));

        webhook(paymentEvent(
                "evt_" + UUID.randomUUID(),
                "PAYMENT_CONFIRMED",
                "pay_" + UUID.randomUUID(),
                subscription,
                "2026-12-10"));

        assertThat(paidUntil(account))
                .isEqualTo(LocalDate.of(2027, 2, 11).atStartOfDay(ZONE).toInstant());
    }

    @Test
    void theLaunchPriceEndsAtTheThirdConfirmedPaymentOnlyOnce() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);

        for (String due : new String[] {"2026-12-10", "2027-01-10"}) {
            webhook(paymentEvent(
                    "evt_" + UUID.randomUUID(), "PAYMENT_CONFIRMED", "pay_" + UUID.randomUUID(), subscription, due));
        }
        assertThat(asaas.valueChanges).isEmpty();
        webhook(paymentEvent(
                "evt_" + UUID.randomUUID(),
                "PAYMENT_CONFIRMED",
                "pay_" + UUID.randomUUID(),
                subscription,
                "2027-02-10"));
        // No 3º pagamento, e não depois: a próxima cobrança já sai pelo preço cheio.
        assertThat(asaas.valueChanges).hasSize(1);
        webhook(paymentEvent(
                "evt_" + UUID.randomUUID(),
                "PAYMENT_CONFIRMED",
                "pay_" + UUID.randomUUID(),
                subscription,
                "2027-03-10"));

        assertThat(asaas.valueChanges).hasSize(1);
        assertThat(asaas.valueChanges.getFirst().get("subscription")).isEqualTo(subscription);
        assertThat((BigDecimal) asaas.valueChanges.getFirst().get("value")).isEqualByComparingTo("200.00");
    }

    @Test
    void aSubscriptionWithoutTheLaunchPriceNeverChangesValue() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        jdbc.sql("update payment_checkout set launch = false, value = 200.00 where professional_id = ?")
                .param(UUID.fromString(account))
                .update();

        for (String due : new String[] {"2026-12-10", "2027-01-10", "2027-02-10", "2027-03-10"}) {
            webhook(paymentEvent(
                    "evt_" + UUID.randomUUID(), "PAYMENT_CONFIRMED", "pay_" + UUID.randomUUID(), subscription, due));
        }

        assertThat(asaas.valueChanges).isEmpty();
    }

    @Test
    void aPaymentOfASubscriptionWithoutAFithubCheckoutIsIgnored() throws Exception {
        String account = registered();

        webhook(paymentEvent(
                        "evt_" + UUID.randomUUID(),
                        "PAYMENT_CONFIRMED",
                        "pay_" + UUID.randomUUID(),
                        "sub_" + UUID.randomUUID(),
                        "2026-12-10"))
                .andExpect(status().isOk());
        // Cobrança avulsa, sem assinatura.
        webhook(paymentEvent(
                        "evt_" + UUID.randomUUID(),
                        "PAYMENT_CONFIRMED",
                        "pay_" + UUID.randomUUID(),
                        null,
                        "2026-12-10"))
                .andExpect(status().isOk());

        assertThat(paidUntil(account)).isNull();
    }

    @Test
    void anEventWithoutTypeIsABadRequest() throws Exception {
        webhook("{\"payment\": {\"id\": \"pay_y\"}}").andExpect(status().isBadRequest());
    }

    @Test
    void otherEventsAreAcceptedAndChangeNothing() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);

        webhook(paymentEvent(
                        "evt_" + UUID.randomUUID(),
                        "PAYMENT_OVERDUE",
                        "pay_" + UUID.randomUUID(),
                        subscription,
                        "2026-12-10"))
                .andExpect(status().isOk());

        assertThat(paidUntil(account)).isNull();
    }

    // --- Um checkout por vez (Code Review do PR #328) ---

    @Test
    void aSecondCheckoutDuringTheTrialReusesTheOpenOne() throws Exception {
        String account = registered();

        String first = checkoutUrl(account);
        String second = checkoutUrl(account);

        assertThat(second).isEqualTo(first);
        assertThat(asaas.checkouts).hasSize(1);
    }

    @Test
    void anOpenCheckoutOlderThanTheReuseWindowIsReplaced() throws Exception {
        String account = registered();
        String first = checkoutUrl(account);
        jdbc.sql(
                        "update payment_checkout set created_at = now() - interval '23 hours 1 minute' where professional_id = ?")
                .param(UUID.fromString(account))
                .update();

        String second = checkoutUrl(account);

        assertThat(second).isNotEqualTo(first);
        assertThat(asaas.checkouts).hasSize(2);
    }

    @Test
    void anOpenCheckoutInsideTheReuseWindowIsReused() throws Exception {
        String account = registered();
        String first = checkoutUrl(account);
        jdbc.sql(
                        "update payment_checkout set created_at = now() - interval '22 hours 59 minutes' where professional_id = ?")
                .param(UUID.fromString(account))
                .update();

        assertThat(checkoutUrl(account)).isEqualTo(first);
    }

    @Test
    void twoSimultaneousCheckoutsOfTheSameProfessionalCreateOnlyOne() throws Exception {
        String account = registered();
        asaas.createDelay = Duration.ofMillis(300);

        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> checkoutUrl(account));
            var second = pool.submit(() -> checkoutUrl(account));

            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(asaas.checkouts).hasSize(1);
    }

    // Grava direto no banco: a aplicação nunca cria checkout de valor zero, a guarda é a CHECK da V7.
    @Test
    void theDatabaseRefusesACheckoutWithoutValue() throws Exception {
        String account = registered();
        checkout(account).andExpect(status().isCreated());
        Object professional = asaas.checkouts.getLast().get("professional");

        assertThatThrownBy(() -> jdbc.sql(
                                "insert into payment_checkout (id, link, professional_id, launch, value, created_at)"
                                        + " values (?, 'https://asaas.test/c', ?, false, 0, now())")
                        .params("chk_" + UUID.randomUUID(), professional)
                        .update())
                .hasMessageContaining("ck_payment_checkout_value");
    }

    @Test
    void anotherProfessionalsOpenCheckoutIsNotReused() throws Exception {
        String one = registered();
        String other = registered();

        assertThat(checkoutUrl(other)).isNotEqualTo(checkoutUrl(one));
    }

    @Test
    void whileTheAsaasSubscriptionLivesThereIsNoNewCheckout() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);

        webhook(subscriptionEvent("SUBSCRIPTION_CREATED", subscription)).andExpect(status().isOk());
        checkout(account).andExpect(status().isConflict());

        assertThat(asaas.checkouts).hasSize(1);
        // A assinatura viva é só da conta dela.
        checkout(registered()).andExpect(status().isCreated());
    }

    @Test
    void afterTheAsaasSubscriptionIsDeletedANewCheckoutIsAllowed() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        webhook(subscriptionEvent("SUBSCRIPTION_CREATED", subscription));

        webhook(subscriptionEvent("SUBSCRIPTION_DELETED", subscription)).andExpect(status().isOk());

        checkout(account).andExpect(status().isCreated());
        assertThat(asaas.checkouts).hasSize(2);
    }

    @Test
    void afterTheAsaasSubscriptionIsInactivatedANewCheckoutIsAllowed() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        webhook(subscriptionEvent("SUBSCRIPTION_CREATED", subscription));

        webhook(subscriptionEvent("SUBSCRIPTION_INACTIVATED", subscription)).andExpect(status().isOk());

        checkout(account).andExpect(status().isCreated());
    }

    @Test
    void aSubscriptionCreatedWithoutAFithubCheckoutIsIgnored() throws Exception {
        webhook(subscriptionEvent("SUBSCRIPTION_CREATED", "sub_" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    // --- Evento incompleto não vira erro (Code Review do PR #328) ---

    @Test
    void aSubscriptionEventWithoutTheSubscriptionIsIgnored() throws Exception {
        webhook("""
                        {"id": "evt_%s", "event": "SUBSCRIPTION_CREATED"}
                        """.formatted(UUID.randomUUID())).andExpect(status().isOk());
    }

    @Test
    void aConfirmedPaymentEventWithoutThePaymentIsIgnored() throws Exception {
        webhook("""
                        {"id": "evt_%s", "event": "PAYMENT_CONFIRMED"}
                        """.formatted(UUID.randomUUID())).andExpect(status().isOk());
    }

    @Test
    void aConfirmedPaymentWithoutIdOrDueDateIsIgnored() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);

        webhook(paymentEvent("evt_" + UUID.randomUUID(), "PAYMENT_CONFIRMED", null, subscription, "2026-12-10"))
                .andExpect(status().isOk());
        webhook(paymentEvent(
                        "evt_" + UUID.randomUUID(),
                        "PAYMENT_CONFIRMED",
                        "pay_" + UUID.randomUUID(),
                        subscription,
                        null))
                .andExpect(status().isOk());

        assertThat(paidUntil(account)).isNull();
    }

    @Test
    void aConfirmedPaymentWithoutValueIsIgnored() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);

        webhook(paymentEvent(
                                "evt_" + UUID.randomUUID(),
                                "PAYMENT_CONFIRMED",
                                "pay_" + UUID.randomUUID(),
                                subscription,
                                "2026-12-10")
                        .replace("\"value\": 150.00", "\"value\": null"))
                .andExpect(status().isOk());

        assertThat(paidUntil(account)).isNull();
    }

    // Grava direto no banco: as chaves da V7 recusam linha duplicada ou órfã, que a aplicação nunca grava.
    @Test
    void theDatabaseRefusesDuplicateOrOrphanPaymentRows() throws Exception {
        String account = registered();
        checkout(account).andExpect(status().isCreated());
        String checkoutId = (String) asaas.checkouts.getLast().get("id");
        UUID professional = (UUID) asaas.checkouts.getLast().get("professional");
        UUID stranger = UUID.randomUUID();
        String subscription = "sub_" + UUID.randomUUID();
        insertSubscription(subscription, checkoutId, professional);
        String other = "chk_" + UUID.randomUUID();
        insertCheckout(other, professional);

        assertRefusedBy("payment_checkout_pkey", () -> insertCheckout(checkoutId, professional));
        assertRefusedBy(
                "payment_checkout_professional_id_fkey", () -> insertCheckout("chk_" + UUID.randomUUID(), stranger));
        assertRefusedBy("payment_subscription_pkey", () -> insertSubscription(subscription, other, professional));
        assertRefusedBy(
                "payment_subscription_checkout_id_fkey",
                () -> insertSubscription("sub_" + UUID.randomUUID(), "chk_" + UUID.randomUUID(), professional));
        assertRefusedBy(
                "payment_subscription_professional_id_fkey",
                () -> insertSubscription("sub_" + UUID.randomUUID(), other, stranger));
        assertRefusedBy(
                "subscription_payment_subscription_id_fkey",
                () -> insertPayment("pay_" + UUID.randomUUID(), "sub_" + UUID.randomUUID(), professional));
        assertRefusedBy(
                "subscription_payment_professional_id_fkey",
                () -> insertPayment("pay_" + UUID.randomUUID(), subscription, stranger));
    }

    private void insertCheckout(String id, UUID professional) {
        jdbc.sql("insert into payment_checkout (id, link, professional_id, launch, value, created_at)"
                        + " values (?, 'https://asaas.test/c', ?, false, 150, now())")
                .params(id, professional)
                .update();
    }

    private void insertSubscription(String id, String checkoutId, UUID professional) {
        jdbc.sql(
                        "insert into payment_subscription (id, checkout_id, professional_id, created_at) values (?, ?, ?, now())")
                .params(id, checkoutId, professional)
                .update();
    }

    private void insertPayment(String id, String subscription, UUID professional) {
        jdbc.sql("insert into subscription_payment (payment_id, subscription_id, professional_id, value, due_date,"
                        + " confirmed_at) values (?, ?, ?, 150, current_date, now())")
                .params(id, subscription, professional)
                .update();
    }

    private static void assertRefusedBy(String constraint, Runnable write) {
        assertThatThrownBy(write::run).as(constraint).hasMessageContaining(constraint);
    }

    @Test
    void anotherSubscriptionForAnAlreadyLinkedCheckoutIsIgnored() throws Exception {
        String account = registered();
        String subscription = paidCheckout(account);
        webhook(subscriptionEvent("SUBSCRIPTION_CREATED", subscription));
        String other = "sub_" + UUID.randomUUID();
        asaas.checkoutOfSubscription.put(other, asaas.checkoutOfSubscription.get(subscription));

        webhook(paymentEvent(
                        "evt_" + UUID.randomUUID(),
                        "PAYMENT_CONFIRMED",
                        "pay_" + UUID.randomUUID(),
                        other,
                        "2026-12-10"))
                .andExpect(status().isOk());

        assertThat(paidUntil(account)).isNull();
    }

    // --- Apoio ---

    private String checkoutUrl(String account) throws Exception {
        String body = checkout(account)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.checkoutUrl");
    }

    private static String subscriptionEvent(String type, String subscription) {
        return """
                {"id": "evt_%s", "event": "%s",
                 "subscription": {"object": "subscription", "id": "%s", "customer": "cus_000", "value": 150.00,
                                  "cycle": "MONTHLY", "billingType": "CREDIT_CARD", "status": "ACTIVE"}}
                """.formatted(UUID.randomUUID(), type, subscription);
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

    private ResultActions checkout(String account) throws Exception {
        return mvc.perform(
                post("/professionals/me/subscription/checkout").header("Authorization", "Bearer conta-" + account));
    }

    /** Cria o checkout e simula que o Asaas criou a assinatura dele: devolve o id da assinatura. */
    private String paidCheckout(String account) throws Exception {
        checkout(account).andExpect(status().isCreated());
        String checkout = (String) asaas.checkouts.getLast().get("id");
        String subscription = "sub_" + UUID.randomUUID();
        asaas.checkoutOfSubscription.put(subscription, checkout);
        return subscription;
    }

    private ResultActions webhook(String body) throws Exception {
        return mvc.perform(post("/webhooks/asaas")
                .header("asaas-access-token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String paymentEvent(String id, String type, String payment, String subscription, String due) {
        return """
                {"id": "%s", "event": "%s", "dateCreated": "2026-12-10 10:00:00",
                 "payment": {"object": "payment", "id": %s, "customer": "cus_000", "subscription": %s,
                             "value": 150.00, "netValue": 145.00, "billingType": "CREDIT_CARD", "status": "CONFIRMED",
                             "dueDate": %s, "description": "FitHub mensal"}}
                """.formatted(id, type, json(payment), json(subscription), json(due));
    }

    /** O texto entre aspas no JSON, ou {@code null} sem aspas. */
    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private Instant paidUntil(String account) {
        // O paid_until é nulo enquanto nada foi pago, e o single() do JdbcClient não aceita valor nulo.
        Timestamp paid = jdbc.sql("select paid_until from subscription where professional_id = ?")
                .param(UUID.fromString(account))
                .query((row, index) -> row.getTimestamp("paid_until"))
                .list()
                .getFirst();
        return paid == null ? null : paid.toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private Instant trialEndsAt(String account) {
        return jdbc.sql("select trial_ends_at from subscription where professional_id = ?")
                .param(UUID.fromString(account))
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private long subscriptionEvents(String account) {
        return jdbc.sql("select count(*) from fithub_outbox_event where routing_key = 'account.subscription-changed.v1'"
                        + " and payload ->> 'professionalId' = ?")
                .param(account)
                .query(Long.class)
                .single();
    }
}
