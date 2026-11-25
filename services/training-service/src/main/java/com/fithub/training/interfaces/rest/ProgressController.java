package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.TrainingProgress;
import com.fithub.training.domain.access.ClientNotFoundException;
import com.fithub.training.domain.progress.InvalidProgressException;
import com.fithub.training.domain.progress.TrainingAdherence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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
 * Evolução do treino para o personal (DEV-0091, FR-56, FR-59): a do aluno vinculado, nas fichas do personal, e a
 * última atividade de cada aluno. Sem vínculo ativo, 404 (BR-02); a nutricionista não tem rota (BR-03).
 */
@RestController
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Evolução do treino", description = "Aderência, carga e volume, e a última atividade dos alunos")
class ProgressController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final TrainingProgress progress;

    ProgressController(TrainingProgress progress) {
        this.progress = progress;
    }

    @Schema(name = "TrainingAdherenceWeek", description = "Uma semana do período: treinos feitos e esperados.")
    record WeekResponse(LocalDate start, LocalDate end, int done, BigDecimal expected) {}

    @Schema(name = "TrainingAdherence", description = "Treinos finalizados contra esperados no período.")
    record AdherenceResponse(
            int done,

            @Schema(description = "Treinos esperados: as divisões da ficha por semana, na proporção dos dias.")
            BigDecimal expected,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Ausente sem treino esperado.")
            BigDecimal percent,

            List<WeekResponse> weeks) {}

    @Schema(name = "ExerciseProgressPoint", description = "Um treino com o exercício: a maior carga e o volume.")
    record PointResponse(
            LocalDate date,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Ausente se só houve série sem repetição.")
            BigDecimal maxLoadKg,

            @Schema(description = "Soma de carga vezes repetições.")
            BigDecimal volumeKg) {}

    @Schema(name = "ExerciseProgress", description = "A evolução de carga e volume de um exercício no período.")
    record ExerciseResponse(UUID exerciseId, String name, List<PointResponse> points) {}

    @Schema(name = "TrainingProgress", description = "A evolução do treino no período (FR-56, FR-58).")
    record ProgressResponse(
            LocalDate from, LocalDate to, AdherenceResponse adherence, List<ExerciseResponse> exercises) {

        static ProgressResponse of(TrainingProgress.View view) {
            TrainingAdherence.Result adherence = view.adherence();
            return new ProgressResponse(
                    view.period().from(),
                    view.period().to(),
                    new AdherenceResponse(
                            adherence.done(),
                            adherence.expected(),
                            adherence.percent(),
                            adherence.weeks().stream()
                                    .map(week ->
                                            new WeekResponse(week.start(), week.end(), week.done(), week.expected()))
                                    .toList()),
                    view.exercises().stream()
                            .map(exercise -> new ExerciseResponse(
                                    exercise.exerciseId(),
                                    exercise.exercise() == null
                                            ? null
                                            : exercise.exercise().name(),
                                    exercise.points().stream()
                                            .map(point -> new PointResponse(
                                                    point.date(), point.maxLoadKg(), point.volumeKg()))
                                            .toList()))
                            .toList());
        }
    }

    @Schema(name = "ClientActivity", description = "A última atividade de um aluno vinculado.")
    record ActivityResponse(
            UUID clientId,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "O último treino finalizado nas fichas do personal; ausente se não houver.")
            Instant lastActivityAt) {}

    @GetMapping(path = "/clients/{clientId}/progress/training", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getClientTrainingProgress",
            summary = "A evolução do treino do aluno vinculado no período (padrão: últimas 4 semanas)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Aderência e evolução de carga e volume."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Período invertido ou maior que 26 semanas; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ProgressResponse ofClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ProgressResponse.of(progress.ofClient(jwt.getSubject(), clientId, from, to));
    }

    @GetMapping(path = "/professionals/me/clients/activity", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listClientTrainingActivity",
            summary = "A última atividade de cada aluno com vínculo ativo, nas fichas do personal",
            responses = {
                @ApiResponse(responseCode = "200", description = "Um item por aluno vinculado."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de personal.",
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
        return ProgressProblems.invalid(exception);
    }

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail notFound(ClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }
}
