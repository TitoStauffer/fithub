package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageLinks;
import com.fithub.client.application.ReadClientAsProfessional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
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
 * Clientes com vínculo ativo do profissional autenticado (DEV-0037). Só o nome e a data do vínculo: perfil e
 * saúde são lidos pela DEV-0040, com a verificação de vínculo.
 */
@RestController
@RequestMapping("/professionals/me/clients")
@PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
@Tag(name = "Vínculos", description = "Vínculos entre cliente e profissional")
class ProfessionalClientsController {

    private final ManageLinks manageLinks;
    private final ReadClientAsProfessional readClient;

    ProfessionalClientsController(ManageLinks manageLinks, ReadClientAsProfessional readClient) {
        this.manageLinks = manageLinks;
        this.readClient = readClient;
    }

    @GetMapping(path = "/{clientId}/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Perfil de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil do cliente."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
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
    ClientProfileController.ProfileResponse profile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return ClientProfileController.ProfileResponse.of(readClient.profile(jwt.getSubject(), clientId));
    }

    @GetMapping(path = "/{clientId}/health", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Perfil de saúde de um cliente com vínculo ativo (cada leitura fica registrada)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil de saúde do cliente."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo, ou o cliente ainda não informou a saúde.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    HealthProfileController.HealthResponse health(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return HealthProfileController.HealthResponse.of(readClient.health(jwt.getSubject(), clientId));
    }

    @ExceptionHandler(ReadClientAsProfessional.NotFoundException.class)
    ProblemDetail notFound(ReadClientAsProfessional.NotFoundException exception) {
        return InvitationController.problem(HttpStatus.NOT_FOUND, "Não encontrado", exception.getMessage());
    }

    @Schema(name = "ProfessionalClient")
    record ClientResponse(UUID linkId, UUID clientId, String clientName, Instant startedAt) {}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Clientes com vínculo ativo do profissional autenticado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Vínculos ativos, do mais novo para o mais antigo."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    List<ClientResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return manageLinks.listClients(jwt.getSubject()).stream()
                .map(client ->
                        new ClientResponse(client.linkId(), client.clientId(), client.clientName(), client.startedAt()))
                .toList();
    }
}
