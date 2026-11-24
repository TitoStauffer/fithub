package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ManagePlans;
import com.fithub.training.domain.plan.PlanNotFoundException;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * O aluno lê as versões publicadas da ficha que recebeu (DEV-0061, BR-06): os registros de execução apontam para a
 * versão que estava ativa. O rascunho nunca aparece.
 */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Fichas de treino", description = "Fichas de treino que o personal monta para o aluno")
class MyPlanVersionController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManagePlans managePlans;

    MyPlanVersionController(ManagePlans managePlans) {
        this.managePlans = managePlans;
    }

    @GetMapping(path = "/me/plans/{planId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "As versões publicadas da ficha que o aluno recebeu",
            responses = {
                @ApiResponse(responseCode = "200", description = "Versões publicadas, da primeira para a última."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe, é de outro aluno ou ainda é rascunho.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<PlanController.VersionSummaryResponse> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return managePlans.clientVersions(jwt.getSubject(), planId).stream()
                .map(PlanController.VersionSummaryResponse::of)
                .toList();
    }

    @GetMapping(path = "/me/plans/{planId}/versions/{number}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Uma versão publicada da ficha que o aluno recebeu",
            responses = {
                @ApiResponse(responseCode = "200", description = "Versão."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha ou a versão não existe, é de outro aluno ou não foi publicada.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanController.VersionResponse version(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable int number) {
        return PlanController.VersionResponse.of(managePlans.clientVersion(jwt.getSubject(), planId, number));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    ProblemDetail planNotFound(PlanNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Ficha não encontrada");
        return problem;
    }
}
