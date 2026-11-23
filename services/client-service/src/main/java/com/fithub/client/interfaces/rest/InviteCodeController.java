package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageInviteCode;
import com.fithub.client.application.ProfessionalNotReadyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Código pessoal do profissional autenticado (DEV-0035): consultar e trocar. */
@RestController
@RequestMapping("/professionals/me/invite-code")
@PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
@Tag(name = "Convites", description = "Convites do profissional ao cliente")
class InviteCodeController {

    private final ManageInviteCode manageInviteCode;

    InviteCodeController(ManageInviteCode manageInviteCode) {
        this.manageInviteCode = manageInviteCode;
    }

    @Schema(name = "InviteCode")
    record InviteCodeResponse(
            @Schema(description = "Código pessoal de 8 caracteres.")
            String code) {}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Código pessoal do profissional autenticado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Código pessoal."),
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
                        responseCode = "409",
                        description = "O cadastro de profissional ainda está sendo processado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    InviteCodeResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return new InviteCodeResponse(manageInviteCode.mine(jwt.getSubject()));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Troca o código pessoal; o antigo deixa de valer",
            responses = {
                @ApiResponse(responseCode = "200", description = "Código novo."),
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
                        responseCode = "409",
                        description = "O cadastro de profissional ainda está sendo processado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    InviteCodeResponse rotate(@AuthenticationPrincipal Jwt jwt) {
        return new InviteCodeResponse(manageInviteCode.rotate(jwt.getSubject()));
    }

    @ExceptionHandler(ProfessionalNotReadyException.class)
    ProblemDetail notReady(ProfessionalNotReadyException exception) {
        return InvitationController.problem(HttpStatus.CONFLICT, "Cadastro em processamento", exception.getMessage());
    }
}
