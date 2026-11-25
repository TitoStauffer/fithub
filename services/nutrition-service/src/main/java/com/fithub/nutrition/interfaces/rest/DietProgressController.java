package com.fithub.nutrition.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.nutrition.application.DietProgress;
import com.fithub.nutrition.domain.access.ClientNotFoundException;
import com.fithub.nutrition.domain.progress.InvalidProgressException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evolução da dieta para a nutricionista (DEV-0092, FR-57, FR-59): a do paciente vinculado, nos planos dela, e a última
 * atividade de cada paciente. Sem vínculo ativo, 404 (BR-02); o personal não tem rota (BR-03).
 */
@RestController
@PreAuthorize("hasRole('NUTRITIONIST')")
@Tag(name = "Evolução da dieta", description = "Aderência, alternativas, desvios e a última atividade dos pacientes")
class DietProgressController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final DietProgress progress;

    DietProgressController(DietProgress progress) {
        this.progress = progress;
    }

    @Schema(name = "PatientActivity", description = "A última atividade de um paciente vinculado.")
    record ActivityResponse(
            UUID clientId,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "O último registro de refeição nos planos da nutricionista; ausente se não houver.")
            Instant lastActivityAt) {}

    @GetMapping(path = "/clients/{clientId}/progress/nutrition", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getClientNutritionProgress",
            summary = "A evolução da dieta do paciente vinculado no período (padrão: últimas 4 semanas)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Aderência, alternativas e desvios."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Período invertido ou maior que 26 semanas; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    DietProgressResponses.ProgressResponse ofClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return DietProgressResponses.ProgressResponse.of(progress.ofClient(jwt.getSubject(), clientId, from, to));
    }

    @GetMapping(path = "/professionals/me/clients/activity", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listPatientNutritionActivity",
            summary = "A última atividade de cada paciente com vínculo ativo, nos planos da nutricionista",
            responses = {
                @ApiResponse(responseCode = "200", description = "Um item por paciente vinculado."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<ActivityResponse> activity(@AuthenticationPrincipal Jwt jwt) {
        return progress.activity(jwt.getSubject()).stream()
                .map(each -> new ActivityResponse(each.clientId(), each.lastActivityAt()))
                .toList();
    }

    @ExceptionHandler(InvalidProgressException.class)
    ProblemDetail invalid(InvalidProgressException exception) {
        return DietProgressResponses.invalid(exception);
    }

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail notFound(ClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }
}
