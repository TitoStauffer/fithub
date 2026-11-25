package com.fithub.training.interfaces.rest;

import com.fithub.training.application.TrainingProgress;
import com.fithub.training.domain.progress.InvalidProgressException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A evolução do treino do próprio aluno (DEV-0091, FR-58), em todas as fichas dele, inclusive a própria. */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Minha evolução", description = "A evolução do treino do aluno")
class MyProgressController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final TrainingProgress progress;

    MyProgressController(TrainingProgress progress) {
        this.progress = progress;
    }

    @GetMapping(path = "/me/progress/training", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getMyTrainingProgress",
            summary = "A evolução do treino do aluno no período (padrão: últimas 4 semanas)",
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
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ProgressController.ProgressResponse mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ProgressController.ProgressResponse.of(progress.mine(jwt.getSubject(), from, to));
    }

    @ExceptionHandler(InvalidProgressException.class)
    ProblemDetail invalid(InvalidProgressException exception) {
        return ProgressProblems.invalid(exception);
    }
}
