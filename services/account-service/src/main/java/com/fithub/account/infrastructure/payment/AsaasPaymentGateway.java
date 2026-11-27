package com.fithub.account.infrastructure.payment;

import com.fithub.account.domain.payment.PaymentGateway;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * O Asaas pelo checkout hospedado (DEV-0100): só cartão de crédito, em assinatura mensal. O checkout vai sem
 * {@code customerData}, então o pagador informa os dados (inclusive o CPF/CNPJ) na página do Asaas, e nada deles
 * passa pelo FitHub. A chave vai no header {@code access_token}.
 */
@Component
class AsaasPaymentGateway implements PaymentGateway {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Imagem do item na página do checkout, que o Asaas exige: um PNG de 1x1 transparente, sem marca de terceiro.
     */
    static final String ITEM_IMAGE =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    /** O checkout fica aberto por 1 dia, o máximo do Asaas. */
    static final int MINUTES_TO_EXPIRE = 1440;

    private final RestClient http;
    private final PaymentSettings.Asaas settings;

    // Montado aqui, como o cliente do Keycloak: o RestClient.Builder do Spring Boot 4 pede um módulo a mais.
    @Autowired
    AsaasPaymentGateway(PaymentSettings.Asaas settings) {
        this(
                RestClient.builder()
                        .requestFactory(timeouts())
                        .baseUrl(settings.baseUrl())
                        .defaultHeader("access_token", settings.apiKey())
                        .build(),
                settings);
    }

    /** Para os testes, com um {@link RestClient} já montado. */
    AsaasPaymentGateway(RestClient http, PaymentSettings.Asaas settings) {
        this.http = http;
        this.settings = settings;
    }

    private static JdkClientHttpRequestFactory timeouts() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    record CheckoutResponse(String id, String link) {}

    record SubscriptionResponse(String id, String checkoutSession) {}

    @Override
    public Checkout createCheckout(UUID professionalId, BigDecimal value, LocalDate firstDueDate) {
        Map<String, Object> body = Map.of(
                "billingTypes", List.of("CREDIT_CARD"),
                "chargeTypes", List.of("RECURRENT"),
                "minutesToExpire", MINUTES_TO_EXPIRE,
                "externalReference", professionalId.toString(),
                "callback", Map.of("successUrl", settings.successUrl(), "cancelUrl", settings.cancelUrl()),
                "items",
                        List.of(Map.of(
                                "name",
                                "FitHub mensal",
                                "description",
                                "Assinatura mensal do FitHub para o profissional",
                                "quantity",
                                1,
                                "value",
                                value,
                                "imageBase64",
                                ITEM_IMAGE)),
                "subscription", Map.of("cycle", "MONTHLY", "nextDueDate", firstDueDate.toString()));
        CheckoutResponse response = http.post()
                .uri("/checkouts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CheckoutResponse.class);
        if (response == null || response.id() == null || response.link() == null) {
            throw new IllegalStateException("O Asaas não devolveu o checkout.");
        }
        return new Checkout(response.id(), response.link());
    }

    @Override
    public Optional<String> checkoutOf(String subscriptionId) {
        SubscriptionResponse response =
                http.get().uri("/subscriptions/{id}", subscriptionId).retrieve().body(SubscriptionResponse.class);
        return Optional.ofNullable(response).map(SubscriptionResponse::checkoutSession);
    }

    @Override
    public void changeValue(String subscriptionId, BigDecimal value) {
        http.put()
                .uri("/subscriptions/{id}", subscriptionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("value", value, "updatePendingPayments", true))
                .retrieve()
                .toBodilessEntity();
    }
}
