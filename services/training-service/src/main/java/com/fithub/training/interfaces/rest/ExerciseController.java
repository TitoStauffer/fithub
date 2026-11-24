package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.SearchExercises;
import com.fithub.training.application.ViewExercise;
import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseNotFoundException;
import com.fithub.training.domain.exercise.ExerciseSearch;
import com.fithub.training.domain.exercise.InvalidExerciseException;
import com.fithub.training.domain.exercise.MuscleGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exercícios vistos pelo personal (DEV-0053, FR-22): o catálogo comum e os próprios (BR-13). */
@RestController
@RequestMapping("/exercises")
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Exercícios", description = "Catálogo de exercícios e exercícios próprios do personal")
class ExerciseController {

    private final ViewExercise viewExercise;
    private final SearchExercises searchExercises;

    ExerciseController(ViewExercise viewExercise, SearchExercises searchExercises) {
        this.viewExercise = viewExercise;
        this.searchExercises = searchExercises;
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

    @Schema(name = "ExercisePage", description = "Uma página da busca, ordenada por nome.")
    record ExercisePageResponse(
            List<ExerciseResponse> items,

            @Schema(description = "Quantos exercícios atendem à busca, somando todas as páginas.")
            long total,

            int page,
            int size) {}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Busca no catálogo e nos exercícios próprios do personal",
            description = "Filtros opcionais, combinados (E lógico). O nome busca por trecho, sem diferenciar"
                    + " maiúsculas nem acentos. Página a partir de 0, com 20 itens por padrão e até 50.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página da busca."),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Filtro fora da lista, página negativa ou tamanho fora de 1 a 50; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
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
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ExercisePageResponse search(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Trecho do nome.") @RequestParam(required = false) String name,
            @Parameter(schema = @Schema(implementation = MuscleGroup.class)) @RequestParam(required = false)
                    String muscleGroup,
            @Parameter(schema = @Schema(implementation = Equipment.class)) @RequestParam(required = false)
                    String equipment,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
                    @RequestParam(required = false)
                    String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "50"))
                    @RequestParam(required = false)
                    String size) {
        // Tudo chega como texto para o erro sair com o campo, em vez do 400 genérico de conversão do Spring.
        ExerciseSearch search = new ExerciseSearch(
                name,
                parseEnum("muscleGroup", muscleGroup, MuscleGroup.class),
                parseEnum("equipment", equipment, Equipment.class),
                parseInt("page", page, 0),
                parseInt("size", size, ExerciseSearch.DEFAULT_SIZE));
        ExerciseSearch.Page result = searchExercises.search(jwt.getSubject(), search);
        return new ExercisePageResponse(
                result.items().stream().map(ExerciseResponse::of).toList(),
                result.total(),
                search.page(),
                search.size());
    }

    private static <E extends Enum<E>> E parseEnum(String field, String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException unknown) {
            throw new InvalidExerciseException(field, "Valor fora da lista.");
        }
    }

    private static int parseInt(String field, String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException notANumber) {
            throw new InvalidExerciseException(field, "Informe um número inteiro.");
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

    @ExceptionHandler(InvalidExerciseException.class)
    ProblemDetail invalid(InvalidExerciseException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(ExerciseNotFoundException.class)
    ProblemDetail notFound(ExerciseNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Exercício não encontrado");
        return problem;
    }
}
