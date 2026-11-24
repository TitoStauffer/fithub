package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ManageOwnExercises;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseArchivedException;
import com.fithub.training.domain.exercise.ExerciseNotFoundException;
import com.fithub.training.domain.exercise.InvalidExerciseException;
import com.fithub.training.domain.exercise.MuscleGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercícios próprios do personal (DEV-0056, FR-24): criar, editar e arquivar. O dono vem do token; exercício de
 * outro personal ou do catálogo responde 404, sem revelar que existe (BR-13).
 */
@RestController
@RequestMapping("/exercises")
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Exercícios", description = "Catálogo de exercícios e exercícios próprios do personal")
class OwnExerciseController {

    private final ManageOwnExercises manageOwnExercises;

    OwnExerciseController(ManageOwnExercises manageOwnExercises) {
        this.manageOwnExercises = manageOwnExercises;
    }

    @Schema(name = "ExerciseInput", description = "Exercício próprio, com as mesmas regras do modelo.")
    record ExerciseRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 2 a 120 caracteres.")
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Um ou mais grupos musculares.")
            List<MuscleGroup> muscleGroups,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Equipment equipment,

            @Schema(nullable = true, description = "Até 4000 caracteres.")
            String instructions,

            @Schema(nullable = true, description = "Vídeo ou imagem da execução, https de até 500 caracteres.")
            String mediaUrl) {

        ManageOwnExercises.Draft toDraft() {
            return new ManageOwnExercises.Draft(
                    name,
                    muscleGroups == null
                            ? null
                            : Set.copyOf(muscleGroups.stream()
                                    .filter(group -> group != null)
                                    .toList()),
                    equipment,
                    instructions,
                    mediaUrl);
        }
    }

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cria um exercício próprio do personal",
            responses = {
                @ApiResponse(responseCode = "201", description = "Exercício criado; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<ExerciseController.ExerciseResponse> create(
            @AuthenticationPrincipal Jwt jwt, @RequestBody ExerciseRequest request) {
        Exercise exercise = manageOwnExercises.create(jwt.getSubject(), request.toDraft());
        return ResponseEntity.created(URI.create("/exercises/" + exercise.id()))
                .body(ExerciseController.ExerciseResponse.of(exercise));
    }

    @PutMapping(
            path = "/{exerciseId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Edita um exercício próprio do personal",
            responses = {
                @ApiResponse(responseCode = "200", description = "Exercício editado."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O exercício não existe, é de outro personal ou do catálogo.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O exercício foi arquivado e não muda mais.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ExerciseController.ExerciseResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId, @RequestBody ExerciseRequest request) {
        return ExerciseController.ExerciseResponse.of(
                manageOwnExercises.update(jwt.getSubject(), exerciseId, request.toDraft()));
    }

    @DeleteMapping(path = "/{exerciseId}")
    @Operation(
            summary = "Arquiva um exercício próprio do personal",
            description = "Não apaga: as fichas antigas continuam mostrando o exercício, que sai da busca."
                    + " Arquivar de novo não muda nada.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Exercício arquivado.", content = @Content()),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O exercício não existe, é de outro personal ou do catálogo.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId) {
        manageOwnExercises.archive(jwt.getSubject(), exerciseId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidExerciseException.class)
    ProblemDetail invalid(InvalidExerciseException exception) {
        return ExerciseProblems.invalid(exception);
    }

    @ExceptionHandler(ExerciseNotFoundException.class)
    ProblemDetail notFound(ExerciseNotFoundException exception) {
        return ExerciseProblems.notFound(exception);
    }

    @ExceptionHandler(ExerciseArchivedException.class)
    ProblemDetail archived(ExerciseArchivedException exception) {
        return ExerciseProblems.archived(exception);
    }
}
