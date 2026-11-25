package com.fithub.nutrition.interfaces.rest;

import com.fithub.nutrition.application.DietProgress;
import com.fithub.nutrition.domain.progress.InvalidProgressException;
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

/** A evolução da dieta do próprio paciente (DEV-0092, FR-58). */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Minha evolução da dieta", description = "A evolução da dieta do paciente")
class MyDietProgressController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final DietProgress progress;

    MyDietProgressController(DietProgress progress) {
        this.progress = progress;
    }

    @GetMapping(path = "/me/progress/nutrition", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getMyNutritionProgress",
            summary = "A evolução da dieta do paciente no período (padrão: últimas 4 semanas)",
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
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    DietProgressResponses.ProgressResponse mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return DietProgressResponses.ProgressResponse.of(progress.mine(jwt.getSubject(), from, to));
    }

    @ExceptionHandler(InvalidProgressException.class)
    ProblemDetail invalid(InvalidProgressException exception) {
        return DietProgressResponses.invalid(exception);
    }
}
