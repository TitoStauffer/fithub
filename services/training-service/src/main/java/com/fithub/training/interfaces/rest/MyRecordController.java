package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.ClientRecords;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.record.InvalidRecordException;
import com.fithub.training.domain.record.ManualRecord;
import com.fithub.training.domain.record.PersonalRecords;
import com.fithub.training.domain.record.RecordNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.net.URI;
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

/** Recordes pessoais do aluno (DEV-0068, FR-53, FR-54): ler e lançar à mão. */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Recordes", description = "Recordes pessoais por exercício")
class MyRecordController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ClientRecords clientRecords;

    MyRecordController(ClientRecords clientRecords) {
        this.clientRecords = clientRecords;
    }

    @Schema(name = "RecordMark", description = "O valor do recorde e a marca que o deu.")
    record MarkResponse(
            @Schema(description = "A carga (maior carga) ou o 1RM estimado, em kg.")
            BigDecimal valueKg,

            BigDecimal loadKg,
            int reps,
            LocalDate date,
            PersonalRecords.Source source,

            @Schema(description = "A série ou o lançamento manual de origem.")
            UUID sourceId) {

        static MarkResponse of(PersonalRecords.Mark mark) {
            PersonalRecords.Performance performance = mark.performance();
            return new MarkResponse(
                    mark.valueKg(),
                    performance.loadKg(),
                    performance.reps(),
                    performance.date(),
                    performance.source(),
                    performance.sourceId());
        }
    }

    @Schema(name = "ExerciseRecords", description = "Recordes de um exercício.")
    record ExerciseRecordsResponse(
            UUID exerciseId,
            String exerciseName,
            MarkResponse maxLoad,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Por Epley, só de 1 a 12 repetições; ausente sem marca nessa faixa.")
            MarkResponse estimatedOneRepMax) {

        static List<ExerciseRecordsResponse> of(ClientRecords.Records records) {
            Map<UUID, Exercise> exercises = records.exercises();
            return records.byExercise().stream()
                    .map(entry -> new ExerciseRecordsResponse(
                            entry.exerciseId(),
                            exercises.containsKey(entry.exerciseId())
                                    ? exercises.get(entry.exerciseId()).name()
                                    : "Exercício indisponível",
                            MarkResponse.of(entry.maxLoad()),
                            entry.estimatedOneRepMax().map(MarkResponse::of).orElse(null)))
                    .toList();
        }
    }

    @Schema(name = "ManualRecordInput", description = "Uma marca feita fora das séries registradas.")
    record ManualRecordRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID exerciseId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 0,25 a 1000 kg, em passos de 0,25.")
            BigDecimal loadKg,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 1 a 300.")
            Integer reps,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Não pode ser no futuro.")
            LocalDate performedOn) {}

    @Schema(name = "ManualRecord", description = "Recorde lançado à mão.")
    record ManualRecordResponse(UUID id, UUID exerciseId, BigDecimal loadKg, int reps, LocalDate performedOn) {

        static ManualRecordResponse of(ManualRecord record) {
            return new ManualRecordResponse(
                    record.id(), record.exerciseId(), record.loadKg(), record.reps(), record.performedOn());
        }
    }

    @GetMapping(path = "/me/records", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listMyRecords",
            summary = "Os recordes do aluno por exercício: maior carga e 1RM estimado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Recordes (a lista pode ser vazia)."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<ExerciseRecordsResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return ExerciseRecordsResponse.of(clientRecords.mine(jwt.getSubject()));
    }

    @PostMapping(
            path = "/me/records",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "addManualRecord",
            summary = "Lança um recorde à mão",
            responses = {
                @ApiResponse(responseCode = "201", description = "Recorde lançado; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Campo fora das regras, data no futuro ou exercício fora do catálogo e das fichas.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<ManualRecordResponse> add(
            @AuthenticationPrincipal Jwt jwt, @RequestBody ManualRecordRequest request) {
        ManualRecord record = clientRecords.addManual(
                jwt.getSubject(),
                new ClientRecords.ManualDraft(
                        request.exerciseId(), request.loadKg(), request.reps(), request.performedOn()));
        return ResponseEntity.created(URI.create("/me/records/" + record.id())).body(ManualRecordResponse.of(record));
    }

    @DeleteMapping(path = "/me/records/{recordId}")
    @Operation(
            operationId = "removeManualRecord",
            summary = "Apaga um recorde lançado à mão",
            responses = {
                @ApiResponse(responseCode = "204", description = "Recorde apagado.", content = @Content()),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O recorde não existe ou é de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID recordId) {
        clientRecords.removeManual(jwt.getSubject(), recordId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidRecordException.class)
    ProblemDetail invalid(InvalidRecordException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(RecordNotFoundException.class)
    ProblemDetail notFound(RecordNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recorde não encontrado");
        return problem;
    }
}
