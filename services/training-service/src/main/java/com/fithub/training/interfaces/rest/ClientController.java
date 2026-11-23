package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ClientAccess;
import com.fithub.training.domain.access.ClientNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cliente visto pelo personal vinculado (DEV-0039): dados básicos da projeção local, com a verificação de acesso
 * por vínculo que todo endpoint com {@code clientId} usa.
 */
@RestController
@RequestMapping("/clients")
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Clientes", description = "Clientes vinculados ao personal")
class ClientController {

    private final ClientAccess clientAccess;

    ClientController(ClientAccess clientAccess) {
        this.clientAccess = clientAccess;
    }

    @Schema(name = "LinkedClient", description = "Dados básicos do cliente vinculado.")
    record ClientResponse(
            UUID clientId,
            String name,
            @Schema(nullable = true) LocalDate birthDate,
            @Schema(nullable = true) String gender) {}

    /** Forma do erro em application/problem+json, só para o contrato (aprendizado contratos-api.md). */
    @Schema(name = "Problem", description = "Erro no formato Problem Details (RFC 9457).")
    record ApiProblem(
            String type,
            String title,
            int status,
            String detail,
            @Schema(nullable = true) String instance) {}

    @GetMapping(path = "/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Dados básicos de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Cliente vinculado."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o cliente (ou ele não existe).",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    ClientResponse client(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        var client = clientAccess.viewClient(jwt.getSubject(), clientId);
        return new ClientResponse(client.clientId(), client.name(), client.birthDate(), client.gender());
    }

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail notFound(ClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Cliente não encontrado");
        return problem;
    }
}
