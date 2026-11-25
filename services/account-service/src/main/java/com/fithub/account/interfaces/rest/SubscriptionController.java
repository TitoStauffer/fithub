package com.fithub.account.interfaces.rest;

import com.fithub.account.application.MySubscription;
import com.fithub.account.domain.subscription.Subscription;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A assinatura do profissional da conta autenticada (DEV-0099). Só existe "a minha": a conta vem do token. */
@RestController
@Tag(name = "Assinatura", description = "Plano e estado da assinatura do profissional")
class SubscriptionController {

    private final MySubscription mySubscription;

    SubscriptionController(MySubscription mySubscription) {
        this.mySubscription = mySubscription;
    }

    @Schema(name = "Subscription")
    record SubscriptionResponse(
            Subscription.Plan plan,

            @Schema(description = "TRIAL no teste grátis, ACTIVE com período pago, EXPIRED sem nenhum dos dois.")
            Subscription.Status status,

            Instant trialEndsAt,

            @Schema(description = "Até quando o período pago vale; ausente enquanto nada foi pago.", nullable = true)
            Instant paidUntil,

            @Schema(description = "Até quando o profissional pode prescrever: o maior entre o teste e o pago.")
            Instant validUntil) {

        static SubscriptionResponse of(MySubscription.View view) {
            Subscription subscription = view.subscription();
            return new SubscriptionResponse(
                    subscription.plan(),
                    view.status(),
                    subscription.trialEndsAt(),
                    subscription.paidUntil(),
                    subscription.validUntil());
        }
    }

    @GetMapping(path = "/professionals/me/subscription", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getMySubscription",
            summary = "Plano e estado da assinatura do profissional da conta autenticada",
            responses = {
                @ApiResponse(responseCode = "200", description = "A assinatura, com o estado agora."),
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
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    SubscriptionResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return mySubscription.of(jwt.getSubject()).map(SubscriptionResponse::of).orElseThrow(() -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Esta conta ainda não tem cadastro de profissional.");
            problem.setTitle("Cadastro não encontrado");
            return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
        });
    }
}
