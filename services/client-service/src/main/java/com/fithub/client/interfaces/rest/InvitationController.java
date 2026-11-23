package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageInvitations;
import com.fithub.client.application.ProfessionalNotReadyException;
import com.fithub.client.domain.invitation.InvalidInvitationException;
import com.fithub.client.domain.invitation.Invitation;
import com.fithub.client.domain.invitation.InvitationStateException;
import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.professional.ProfessionalType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.Clock;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convites do profissional ao cliente (DEV-0035, FR-10). Criar, listar e cancelar exigem o papel
 * {@code personal} ou {@code nutritionist}; consultar um convite (tela de aceite) basta estar autenticado. O
 * token e o código vão no corpo, nunca na URL, para não aparecerem em log de acesso.
 */
@RestController
@RequestMapping("/invitations")
@Tag(name = "Convites", description = "Convites do profissional ao cliente")
class InvitationController {

    private static final String PROFESSIONAL = "hasAnyRole('PERSONAL', 'NUTRITIONIST')";

    private final ManageInvitations manageInvitations;
    private final Clock clock;

    InvitationController(ManageInvitations manageInvitations, Clock clock) {
        this.manageInvitations = manageInvitations;
        this.clock = clock;
    }

    @Schema(name = "InvitationRequest")
    record CreateRequest(
            @Schema(description = "E-mail do cliente; sem ele, o convite volta para o profissional compartilhar.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = Invitation.MAX_EMAIL, message = "Informe um e-mail válido.")
            String email) {}

    @Schema(name = "CreatedInvitation")
    record CreatedResponse(
            UUID id,
            @Schema(nullable = true) String email,

            @Schema(description = "Link do convite; só aparece nesta resposta.")
            String link,

            @Schema(description = "Código de 8 caracteres; só aparece nesta resposta.")
            String code,

            Instant expiresAt,

            @Schema(description = "Se o e-mail saiu; false sem e-mail ou se o envio falhou.")
            boolean emailSent) {}

    @Schema(name = "Invitation")
    record InvitationResponse(
            UUID id,
            @Schema(nullable = true) String email,
            Invitation.State state,
            Instant expiresAt,
            Instant createdAt) {

        static InvitationResponse of(Invitation invitation, Instant now) {
            return new InvitationResponse(
                    invitation.id(),
                    invitation.email(),
                    invitation.stateAt(now),
                    invitation.expiresAt(),
                    invitation.createdAt());
        }
    }

    @Schema(name = "InvitationLookupRequest", description = "Informe o token do link ou o código.")
    record LookupRequest(
            @Schema(nullable = true) String token,
            @Schema(nullable = true) String code) {}

    @Schema(name = "InvitationPreview")
    record PreviewResponse(
            ManageInvitations.Preview.Kind kind,
            String professionalName,
            ProfessionalType professionalType,
            Set<SharedData> sharedData,

            @Schema(nullable = true, description = "Vazio para o código pessoal do profissional, que não vence.")
            Instant expiresAt) {}

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PROFESSIONAL)
    @Operation(
            summary = "Cria um convite (por e-mail ou para compartilhar)",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Convite criado.",
                        content = @Content(schema = @Schema(implementation = CreatedResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "E-mail inválido.",
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
    ResponseEntity<CreatedResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        var created = manageInvitations.create(jwt.getSubject(), request.email());
        Invitation invitation = created.invitation();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedResponse(
                        invitation.id(),
                        invitation.email(),
                        created.link(),
                        created.code(),
                        invitation.expiresAt(),
                        created.emailSent()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PROFESSIONAL)
    @Operation(
            summary = "Convites do profissional autenticado, do mais novo para o mais antigo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Convites do profissional."),
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
    List<InvitationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        Instant now = clock.instant();
        return manageInvitations.listMine(jwt.getSubject()).stream()
                .map(invitation -> InvitationResponse.of(invitation, now))
                .toList();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PROFESSIONAL)
    @Operation(
            summary = "Cancela um convite do profissional autenticado",
            responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Convite cancelado (ou já estava).",
                        content = @Content()),
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
                        description = "Convite não encontrado (ou de outro profissional).",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Convite já aceito.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        manageInvitations.cancel(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Consulta um convite pelo token do link ou pelo código, para a tela de aceite",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Quem convida e o que será compartilhado.",
                        content = @Content(schema = @Schema(implementation = PreviewResponse.class))),
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
                        responseCode = "410",
                        description = "Convite vencido, já usado ou cancelado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    PreviewResponse lookup(@RequestBody LookupRequest request) {
        var preview = request.token() != null && !request.token().isBlank()
                ? manageInvitations.previewByToken(request.token())
                : manageInvitations.previewByCode(request.code());
        return new PreviewResponse(
                preview.kind(),
                preview.professionalName(),
                preview.professionalType(),
                preview.sharedData(),
                preview.expiresAt());
    }

    @ExceptionHandler(ManageInvitations.NotFoundException.class)
    ProblemDetail notFound(ManageInvitations.NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Convite não encontrado", exception.getMessage());
    }

    @ExceptionHandler(ManageInvitations.GoneException.class)
    ProblemDetail gone(ManageInvitations.GoneException exception) {
        return problem(HttpStatus.GONE, "Convite sem validade", exception.getMessage());
    }

    @ExceptionHandler(InvitationStateException.class)
    ProblemDetail state(InvitationStateException exception) {
        return problem(HttpStatus.CONFLICT, "Convite já aceito", exception.getMessage());
    }

    @ExceptionHandler(ProfessionalNotReadyException.class)
    ProblemDetail notReady(ProfessionalNotReadyException exception) {
        return problem(HttpStatus.CONFLICT, "Cadastro em processamento", exception.getMessage());
    }

    @ExceptionHandler(InvalidInvitationException.class)
    ProblemDetail invalid(InvalidInvitationException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
