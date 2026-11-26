package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.ExportTrainingData;
import com.fithub.training.domain.account.TrainingExportReader;
import com.fithub.training.domain.plan.WorkoutPlan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação dos dados do cliente no training (DEV-0166, NFR-01, LGPD art. 18, II e V). A conta vem sempre do token.
 * Campo que pode faltar é omitido, não {@code null} (aprendizado nulo-sobre-ref-no-openapi).
 */
@RestController
@Tag(name = "Exportação", description = "Exportação dos dados do cliente autenticado")
class TrainingExportController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ExportTrainingData exportTrainingData;

    TrainingExportController(ExportTrainingData exportTrainingData) {
        this.exportTrainingData = exportTrainingData;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "TrainingExportPlan", description = "Ficha com as versões que o cliente vê.")
    record PlanExport(
            UUID id,

            @Schema(description = "Ausente na ficha própria do cliente.")
            UUID personalId,

            WorkoutPlan.Status status,
            Instant createdAt,
            List<PlanController.VersionResponse> versions) {

        static PlanExport of(ExportTrainingData.PlanWithVersions found) {
            WorkoutPlan plan = found.plan();
            return new PlanExport(
                    plan.id(),
                    plan.personalId(),
                    plan.status(),
                    plan.createdAt(),
                    found.versions().stream()
                            .map(PlanController.VersionResponse::of)
                            .toList());
        }
    }

    @Schema(name = "TrainingExportSet", description = "Série registrada.")
    record SetExport(
            int itemPosition,
            int setNumber,
            UUID exerciseId,
            String exerciseName,
            BigDecimal loadKg,
            int reps,
            Instant recordedAt) {

        static SetExport of(TrainingExportReader.ExportedSet set) {
            return new SetExport(
                    set.itemPosition(),
                    set.setNumber(),
                    set.exerciseId(),
                    set.exerciseName(),
                    set.loadKg(),
                    set.reps(),
                    set.recordedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "TrainingExportSession", description = "Sessão de treino com as séries.")
    record SessionExport(
            UUID id,
            UUID planId,
            int versionNumber,
            int divisionPosition,
            Instant startedAt,

            @Schema(description = "Ausente na sessão ainda aberta.")
            Instant finishedAt,

            @Schema(description = "Ausente se o cliente não comentou.")
            String comment,

            @Schema(description = "De 1 (muito fácil) a 5 (exaustivo); ausente se não informada.")
            Integer feeling,

            List<SetExport> sets) {

        static SessionExport of(TrainingExportReader.ExportedSession session) {
            return new SessionExport(
                    session.id(),
                    session.planId(),
                    session.versionNumber(),
                    session.divisionPosition(),
                    session.startedAt(),
                    session.finishedAt(),
                    session.comment(),
                    session.feeling(),
                    session.sets().stream().map(SetExport::of).toList());
        }
    }

    @Schema(name = "TrainingExport", description = "Todos os dados que o training tem do cliente.")
    record ExportResponse(
            Instant exportedAt,
            List<PlanExport> plans,
            List<SessionExport> sessions,
            List<MyRecordController.ExerciseRecordsResponse> records,
            List<RestrictionController.RestrictionResponse> restrictions) {

        static ExportResponse of(ExportTrainingData.Export export) {
            return new ExportResponse(
                    export.exportedAt(),
                    export.plans().stream().map(PlanExport::of).toList(),
                    export.sessions().stream().map(SessionExport::of).toList(),
                    MyRecordController.ExerciseRecordsResponse.of(export.records()),
                    export.restrictions().stream()
                            .map(RestrictionController.RestrictionResponse::of)
                            .toList());
        }
    }

    @GetMapping(path = "/me/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exporta todos os dados que o training tem do cliente",
            description = "Fichas (as dos personais nas versões publicadas; a própria com os rascunhos), sessões com as"
                    + " séries e os comentários, recordes e restrições de mobilidade. O app junta com a exportação do"
                    + " client-service e do nutrition.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Os dados do cliente."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não tem o papel de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ExportResponse export(@AuthenticationPrincipal Jwt jwt) {
        return ExportResponse.of(exportTrainingData.mine(jwt.getSubject()));
    }
}
