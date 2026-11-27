package com.fithub.account.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** O webhook do Asaas sem token configurado, ou com token curto demais, recusa tudo (DEV-0100). */
class AsaasWebhookAuthTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EVENT = "{\"event\": \"PAYMENT_CONFIRMED\"}";

    @Test
    void withoutAConfiguredTokenEveryRequestIsRefused() {
        AsaasWebhookController controller = new AsaasWebhookController(null, JSON, "");

        assertThat(controller.receive("", EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.receive(null, EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void withAConfiguredTokenARequestWithoutTheHeaderIsRefused() {
        String token = "t".repeat(AsaasWebhookController.MIN_TOKEN_LENGTH);
        AsaasWebhookController controller = new AsaasWebhookController(null, JSON, token);

        assertThat(controller.receive(null, EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenShorterThanTheAsaasMinimumIsTreatedAsNotConfigured() {
        String shortToken = "a".repeat(AsaasWebhookController.MIN_TOKEN_LENGTH - 1);
        AsaasWebhookController controller = new AsaasWebhookController(null, JSON, shortToken);

        assertThat(controller.receive(shortToken, EVENT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anInvalidBodyWithoutTheTokenIsRefusedAs401NotAs400() {
        String token = "t".repeat(AsaasWebhookController.MIN_TOKEN_LENGTH);
        AsaasWebhookController controller = new AsaasWebhookController(null, JSON, token);

        assertThat(controller.receive(null, "não é json").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.receive(token, "não é json").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.receive(token, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
