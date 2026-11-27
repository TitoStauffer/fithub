package com.fithub.account.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fithub.account.domain.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** O que vai para a API do Asaas (DEV-0100): só cartão recorrente, sem dado do pagador, com a chave no header. */
class AsaasPaymentGatewayTest {

    private static final String BASE = "https://api-sandbox.asaas.com/v3";
    private static final PaymentSettings.Asaas SETTINGS = new PaymentSettings.Asaas(
            BASE,
            "chave-de-teste",
            "token",
            "https://fithub.test/assinatura?retorno=sucesso",
            "https://fithub.test/assinatura?retorno=cancelado");

    private MockRestServiceServer server;
    private AsaasPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("access_token", "chave-de-teste");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new AsaasPaymentGateway(builder.build(), SETTINGS);
    }

    @Test
    void theCheckoutIsOnlyRecurringCreditCardWithoutPayerData() {
        UUID professional = UUID.randomUUID();
        server.expect(requestTo(BASE + "/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "chave-de-teste"))
                .andExpect(jsonPath("$.billingTypes", Matchers.contains("CREDIT_CARD")))
                .andExpect(jsonPath("$.chargeTypes", Matchers.contains("RECURRENT")))
                .andExpect(jsonPath("$.subscription.cycle").value("MONTHLY"))
                .andExpect(jsonPath("$.subscription.nextDueDate").value("2026-12-10"))
                .andExpect(jsonPath("$.items[0].value").value(150.0))
                .andExpect(jsonPath("$.externalReference").value(professional.toString()))
                .andExpect(jsonPath("$.callback.successUrl").value("https://fithub.test/assinatura?retorno=sucesso"))
                // O pagador informa os dados na página do Asaas: o FitHub não manda nem guarda CPF.
                .andExpect(jsonPath("$.customerData").doesNotExist())
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andRespond(withSuccess(
                        "{\"id\": \"chk_1\", \"link\": \"https://sandbox.asaas.com/checkoutSession/show?id=chk_1\"}",
                        MediaType.APPLICATION_JSON));

        PaymentGateway.Checkout checkout =
                gateway.createCheckout(professional, new BigDecimal("150.00"), LocalDate.of(2026, 12, 10));

        assertThat(checkout)
                .isEqualTo(new PaymentGateway.Checkout(
                        "chk_1", "https://sandbox.asaas.com/checkoutSession/show?id=chk_1"));
        server.verify();
    }

    /** Sem id ou sem link, o checkout não pode ser gravado nem mostrado: falha em vez de gravar um checkout vazio. */
    @Test
    void aCheckoutResponseWithoutIdLinkOrBodyIsRefused() {
        for (String body : new String[] {"{\"link\": \"https://sandbox.asaas.com/c\"}", "{\"id\": \"chk_1\"}", ""}) {
            server.reset();
            server.expect(requestTo(BASE + "/checkouts")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> gateway.createCheckout(
                            UUID.randomUUID(), new BigDecimal("150.00"), LocalDate.of(2026, 12, 10)))
                    .as("resposta %s", body)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("O Asaas não devolveu o checkout.");
        }
    }

    @Test
    void theCheckoutOfASubscriptionComesFromItsCheckoutSession() {
        server.expect(requestTo(BASE + "/subscriptions/sub_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\": \"sub_1\", \"checkoutSession\": \"chk_1\", \"value\": 150}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.checkoutOf("sub_1")).contains("chk_1");
        server.verify();
    }

    @Test
    void theNewValueAlsoUpdatesThePendingCharges() {
        server.expect(requestTo(BASE + "/subscriptions/sub_1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.value").value(200.0))
                .andExpect(jsonPath("$.updatePendingPayments").value(true))
                .andRespond(withSuccess("{\"id\": \"sub_1\"}", MediaType.APPLICATION_JSON));

        gateway.changeValue("sub_1", new BigDecimal("200.00"));

        server.verify();
    }
}
