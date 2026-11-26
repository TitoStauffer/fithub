package com.fithub.account.interfaces.rest;

import com.fithub.account.application.StartCheckout;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Checkout da assinatura mensal no Asaas (DEV-0100), para o profissional da conta autenticada. */
@RestController
@Tag(name = "Assinatura", description = "Plano e estado da assinatura do profissional")
class CheckoutController {

    private final StartCheckout startCheckout;

    CheckoutController(StartCheckout startCheckout) {
        this.startCheckout = startCheckout;
    }

    @Schema(name = "SubscriptionCheckout")
    record CheckoutResponse(
            @Schema(description = "Página de pagamento do Asaas, só com cartão de crédito.")
            String checkoutUrl,

            @Schema(description = "Valor mensal da primeira cobrança.")
            BigDecimal value,

            @Schema(description = "Se o valor é o preço de lançamento, que vale nos primeiros meses pagos.")
            boolean launchPrice) {}

    @PostMapping(path = "/professionals/me/subscription/checkout", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "startSubscriptionCheckout",
            summary = "Cria o checkout da assinatura mensal no Asaas e devolve o link de pagamento",
            responses = {
                @ApiResponse(responseCode = "201", description = "O checkout, com o link e o valor."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A conta ainda não tem cadastro de profissional.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "A assinatura já tem período pago valendo.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    CheckoutResponse start(@AuthenticationPrincipal Jwt jwt) {
        return switch (startCheckout.start(jwt.getSubject())) {
            case StartCheckout.Result.Created created ->
                new CheckoutResponse(
                        created.link(), created.offer().value(), created.offer().launch());
            case StartCheckout.Result.NotProfessional ignored ->
                throw problem(
                        HttpStatus.NOT_FOUND,
                        "Cadastro não encontrado",
                        "Esta conta ainda não tem cadastro de profissional.");
            case StartCheckout.Result.AlreadyActive ignored ->
                throw problem(HttpStatus.CONFLICT, "Assinatura ativa", "A assinatura já tem período pago valendo.");
        };
    }

    private static ErrorResponseException problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return new ErrorResponseException(status, problem, null);
    }
}
