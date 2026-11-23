package com.fithub.client.interfaces.rest;

import com.fithub.client.application.AcceptInvitation;
import com.fithub.client.application.ManageInvitations;
import com.fithub.client.domain.invitation.InvalidInvitationException;
import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.ConsentTerms;
import com.fithub.client.domain.link.LinkRuleException;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.client.domain.profile.ProfileRequiredException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vínculos do cliente autenticado (DEV-0036). O aceite cria o vínculo e grava o consentimento; o token e o código
 * vão no corpo, nunca na URL.
 */
@RestController
@RequestMapping("/links")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Vínculos", description = "Vínculos entre cliente e profissional")
class LinkController {

    private final AcceptInvitation acceptInvitation;

    LinkController(AcceptInvitation acceptInvitation) {
        this.acceptInvitation = acceptInvitation;
    }

    @Schema(name = "AcceptInvitationRequest", description = "Informe o token do link ou o código.")
    record AcceptRequest(
            @Schema(nullable = true) String token,

            @Schema(nullable = true, description = "Código do convite ou código pessoal do profissional.")
            String code,

            @Schema(description = "Consentimento explícito; precisa ser true.")
            Boolean consent,

            @Schema(description = "Versão do termo que a tela mostrou.", example = ConsentTerms.CURRENT_VERSION)
            String termsVersion) {}

    @Schema(name = "Link")
    record LinkResponse(
            UUID id,
            UUID professionalId,
            String professionalName,
            ProfessionalType professionalType,
            Set<SharedData> sharedData,
            Instant startedAt) {}

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Aceita um convite (ou o código pessoal do profissional) com consentimento",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Vínculo criado e consentimento registrado.",
                        content = @Content(schema = @Schema(implementation = LinkResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Sem o consentimento explícito.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Convite ou código não encontrado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "Já há vínculo ativo do mesmo tipo, vínculo consigo mesmo, termo desatualizado ou perfil ainda não criado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "410",
                        description = "Convite vencido, já usado ou cancelado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<LinkResponse> accept(@AuthenticationPrincipal Jwt jwt, @RequestBody AcceptRequest request) {
        var result = acceptInvitation.accept(new AcceptInvitation.Command(
                jwt.getSubject(), request.token(), request.code(), request.consent(), request.termsVersion()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LinkResponse(
                        result.link().id(),
                        result.professional().id(),
                        result.professional().name(),
                        result.professional().type(),
                        ConsentTerms.SCOPE,
                        result.link().startedAt()));
    }

    @ExceptionHandler(InvalidInvitationException.class)
    ProblemDetail invalid(InvalidInvitationException exception) {
        ProblemDetail problem = InvitationController.problem(
                HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(ManageInvitations.NotFoundException.class)
    ProblemDetail notFound(ManageInvitations.NotFoundException exception) {
        return InvitationController.problem(HttpStatus.NOT_FOUND, "Convite não encontrado", exception.getMessage());
    }

    @ExceptionHandler(ManageInvitations.GoneException.class)
    ProblemDetail gone(ManageInvitations.GoneException exception) {
        return InvitationController.problem(HttpStatus.GONE, "Convite sem validade", exception.getMessage());
    }

    @ExceptionHandler(LinkRuleException.class)
    ProblemDetail rule(LinkRuleException exception) {
        return InvitationController.problem(HttpStatus.CONFLICT, "Vínculo não permitido", exception.getMessage());
    }

    @ExceptionHandler(AcceptInvitation.TermsOutdatedException.class)
    ProblemDetail terms(AcceptInvitation.TermsOutdatedException exception) {
        return InvitationController.problem(HttpStatus.CONFLICT, "Termo desatualizado", exception.getMessage());
    }

    @ExceptionHandler(ProfileRequiredException.class)
    ProblemDetail profileRequired(ProfileRequiredException exception) {
        return InvitationController.problem(HttpStatus.CONFLICT, "Perfil ainda não criado", exception.getMessage());
    }
}
