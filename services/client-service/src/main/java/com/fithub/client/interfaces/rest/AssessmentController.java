package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageAssessments;
import com.fithub.client.domain.assessment.Assessment;
import com.fithub.client.domain.assessment.Circumferences;
import com.fithub.client.domain.assessment.InvalidAssessmentException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Avaliações corporais (DEV-0044). O tipo vem da rota: o cliente registra as online em {@code /clients/me}; o
 * profissional vinculado, as presenciais em {@code /professionals/me/clients/{clientId}}.
 */
@RestController
@Tag(name = "Avaliações", description = "Avaliações corporais do cliente")
class AssessmentController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageAssessments manageAssessments;

    AssessmentController(ManageAssessments manageAssessments) {
        this.manageAssessments = manageAssessments;
    }

    @Schema(name = "Circumferences", description = "Circunferências em cm, todas opcionais.")
    record CircumferencesBody(
            @Schema(nullable = true) BigDecimal neck,
            @Schema(nullable = true) BigDecimal chest,
            @Schema(nullable = true) BigDecimal waist,
            @Schema(nullable = true) BigDecimal abdomen,
            @Schema(nullable = true) BigDecimal hip,
            @Schema(nullable = true) BigDecimal arm,
            @Schema(nullable = true) BigDecimal forearm,
            @Schema(nullable = true) BigDecimal thigh,
            @Schema(nullable = true) BigDecimal calf) {

        Circumferences toDomain() {
            return new Circumferences(neck, chest, waist, abdomen, hip, arm, forearm, thigh, calf);
        }

        static CircumferencesBody of(Circumferences c) {
            return new CircumferencesBody(
                    c.neck(), c.chest(), c.waist(), c.abdomen(), c.hip(), c.arm(), c.forearm(), c.thigh(), c.calf());
        }
    }

    @Schema(name = "AssessmentRequest")
    record AssessmentRequest(
            LocalDate measuredOn,

            @Schema(description = "Peso em kg, de 20 a 400.")
            BigDecimal weightKg,

            @Schema(nullable = true) CircumferencesBody circumferences,

            @Schema(nullable = true, description = "Sexo usado só nos cálculos de % de gordura.")
            Assessment.Sex sexForFormulas) {

        ManageAssessments.Command toCommand() {
            return new ManageAssessments.Command(
                    measuredOn, weightKg, circumferences == null ? null : circumferences.toDomain(), sexForFormulas);
        }
    }

    @Schema(name = "Assessment")
    record AssessmentResponse(
            UUID id,
            Assessment.Type type,
            LocalDate measuredOn,
            BigDecimal weightKg,
            CircumferencesBody circumferences,
            @Schema(nullable = true) Assessment.Sex sexForFormulas,
            UUID authorId,
            Assessment.AuthorRole authorRole,
            Instant createdAt) {

        static AssessmentResponse of(Assessment a) {
            return new AssessmentResponse(
                    a.id(),
                    a.type(),
                    a.measuredOn(),
                    a.weightKg(),
                    CircumferencesBody.of(a.circumferences()),
                    a.sexForFormulas(),
                    a.authorId(),
                    a.authorRole(),
                    a.createdAt());
        }
    }

    @PostMapping(path = "/clients/me/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Registra uma avaliação online (autoaferida) do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "201", description = "Avaliação registrada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo inválido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<AssessmentResponse> recordOnline(
            @AuthenticationPrincipal Jwt jwt, @RequestBody AssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssessmentResponse.of(manageAssessments.recordOnline(jwt.getSubject(), request.toCommand())));
    }

    @GetMapping(path = "/clients/me/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Histórico de avaliações do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Da medição mais recente para a mais antiga."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    List<AssessmentResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return manageAssessments.listMine(jwt.getSubject()).stream()
                .map(AssessmentResponse::of)
                .toList();
    }

    @DeleteMapping("/clients/me/assessments/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exclui uma avaliação do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "204", description = "Avaliação excluída.", content = @Content()),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Avaliação não encontrada (ou de outra pessoa).",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        manageAssessments.deleteMine(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/professionals/me/clients/{clientId}/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
    @Operation(
            summary = "Registra uma avaliação presencial de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "201", description = "Avaliação registrada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo inválido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<AssessmentResponse> recordPresential(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @RequestBody AssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssessmentResponse.of(
                        manageAssessments.recordPresential(jwt.getSubject(), clientId, request.toCommand())));
    }

    @GetMapping(path = "/professionals/me/clients/{clientId}/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
    @Operation(
            summary = "Histórico de avaliações de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Da medição mais recente para a mais antiga."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    List<AssessmentResponse> forClient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return manageAssessments.listForClient(jwt.getSubject(), clientId).stream()
                .map(AssessmentResponse::of)
                .toList();
    }

    @ExceptionHandler(InvalidAssessmentException.class)
    ProblemDetail invalid(InvalidAssessmentException exception) {
        ProblemDetail problem = InvitationController.problem(
                HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(ManageAssessments.NotFoundException.class)
    ProblemDetail notFound(ManageAssessments.NotFoundException exception) {
        return InvitationController.problem(HttpStatus.NOT_FOUND, "Não encontrado", exception.getMessage());
    }
}
