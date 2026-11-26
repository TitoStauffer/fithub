package com.fithub.account.interfaces.rest;

import com.fithub.account.application.HandlePaymentEvent;
import io.swagger.v3.oas.annotations.Hidden;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook do Asaas (DEV-0100). Fica fora do JWT ({@code fithub.security.public-paths}) e só aceita a requisição com o
 * token configurado no header {@code asaas-access-token}, comparado em tempo constante. Sem token configurado, recusa
 * tudo. A resposta não diz por quê. Não entra no OpenAPI: não é API do app.
 */
@Hidden
@RestController
class AsaasWebhookController {

    /** O Asaas exige token de 32 a 255 caracteres. */
    static final int MIN_TOKEN_LENGTH = 32;

    private final HandlePaymentEvent handlePaymentEvent;
    private final byte[] token;

    AsaasWebhookController(
            HandlePaymentEvent handlePaymentEvent, @Value("${fithub.payment.asaas.webhook-token:}") String token) {
        this.handlePaymentEvent = handlePaymentEvent;
        this.token = token.length() >= MIN_TOKEN_LENGTH ? token.getBytes(StandardCharsets.UTF_8) : null;
    }

    /** O evento, só com os campos usados; o resto do payload (inclusive o id do evento) é ignorado. */
    record Event(String event, PaymentPayload payment) {}

    record PaymentPayload(String id, String subscription, BigDecimal value, LocalDate dueDate) {}

    @PostMapping("/webhooks/asaas")
    ResponseEntity<Void> receive(
            @RequestHeader(name = "asaas-access-token", required = false) String received, @RequestBody Event event) {
        if (!authentic(received)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (event.event() == null) {
            return ResponseEntity.badRequest().build();
        }
        PaymentPayload payment = event.payment();
        handlePaymentEvent.handle(
                event.event(),
                payment == null
                        ? null
                        : new HandlePaymentEvent.Payment(
                                payment.id(), payment.subscription(), payment.value(), payment.dueDate()));
        return ResponseEntity.ok().build();
    }

    private boolean authentic(String received) {
        return token != null
                && received != null
                && MessageDigest.isEqual(token, received.getBytes(StandardCharsets.UTF_8));
    }
}
