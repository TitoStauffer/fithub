package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.ViewExercise;
import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseNotFoundException;
import com.fithub.training.domain.exercise.MuscleGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** Exercícios vistos pelo personal (DEV-0053, FR-22): o catálogo comum e os próprios (BR-13). */
@RestController
@RequestMapping("/exercises")
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Exercícios", description = "Catálogo de exercícios e exercícios próprios do personal")
class ExerciseController {

    private final ViewExercise viewExercise;

    ExerciseController(ViewExercise viewExercise) {
        this.viewExercise = viewExercise;
    }

    @Schema(name = "ExerciseAttribution", description = "Origem e licença do exercício importado (ADR-010).")
    record AttributionResponse(
            String source,
            String license,
            String author,
            // Crédito próprio da mídia importada (DEV-0054); ausente quando o item não tem mídia.
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Licença da mídia importada; presente só quando há mídia.")
            String mediaLicense,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Autor da mídia importada; presente só quando há mídia.")
            String mediaAuthor) {

        static AttributionResponse of(Attribution attribution) {
            return attribution == null
                    ? null
                    : new AttributionResponse(
                            attribution.source(),
                            attribution.license(),
                            attribution.author(),
                            attribution.mediaLicense(),
                            attribution.mediaAuthor());
        }
    }

    @Schema(name = "Exercise", description = "Exercício do catálogo ou próprio do personal.")
    record ExerciseResponse(
            UUID id,
            Exercise.Kind kind,
            String name,

            @Schema(description = "Grupos musculares, em ordem alfabética.")
            List<MuscleGroup> muscleGroups,

            Equipment equipment,
            @Schema(nullable = true) String instructions,

            @Schema(nullable = true, description = "Vídeo ou imagem da execução, sempre https.")
            String mediaUrl,

            // Ausente (e não null) em exercício próprio: o contrato declara o campo opcional, sem null. Um
            // `nullable` sobre o $ref geraria um schema impossível (aprendizado nulo-sobre-ref-no-openapi).
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Presente só em exercício importado; a tela mostra o crédito.")
            AttributionResponse attribution) {

        static ExerciseResponse of(Exercise exercise) {
            return new ExerciseResponse(
                    exercise.id(),
                    exercise.kind(),
                    exercise.name(),
                    exercise.muscleGroups().stream().sorted().toList(),
                    exercise.equipment(),
                    exercise.instructions(),
                    exercise.mediaUrl(),
                    AttributionResponse.of(exercise.attribution()));
        }
    }

    @GetMapping(path = "/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Um exercício do catálogo ou próprio do personal",
            responses = {
                @ApiResponse(responseCode = "200", description = "Exercício."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O exercício não existe ou é próprio de outro personal.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ExerciseResponse exercise(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId) {
        return ExerciseResponse.of(viewExercise.view(jwt.getSubject(), exerciseId));
    }

    @ExceptionHandler(ExerciseNotFoundException.class)
    ProblemDetail notFound(ExerciseNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Exercício não encontrado");
        return problem;
    }
}
