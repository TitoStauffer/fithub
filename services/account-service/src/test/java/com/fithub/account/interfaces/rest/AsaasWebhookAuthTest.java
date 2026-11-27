package com.fithub.account.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** O webhook do Asaas sem token configurado, ou com token curto demais, recusa tudo (DEV-0100). */
class AsaasWebhookAuthTest {

    private static final AsaasWebhookController.Event EVENT =
            new AsaasWebhookController.Event("PAYMENT_CONFIRMED", null, null);

    @Test
    void withoutAConfiguredTokenEveryRequestIsRefused() {
        AsaasWebhookController controller = new AsaasWebhookController(null, "");

        assertThat(controller.receive("", EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.receive(null, EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void withAConfiguredTokenARequestWithoutTheHeaderIsRefused() {
        String token = "t".repeat(AsaasWebhookController.MIN_TOKEN_LENGTH);
        AsaasWebhookController controller = new AsaasWebhookController(null, token);

        assertThat(controller.receive(null, EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenShorterThanTheAsaasMinimumIsTreatedAsNotConfigured() {
        String shortToken = "a".repeat(AsaasWebhookController.MIN_TOKEN_LENGTH - 1);
        AsaasWebhookController controller = new AsaasWebhookController(null, shortToken);

        assertThat(controller.receive(shortToken, EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
