package com.fithub.nutrition.interfaces.rest;

import com.fithub.nutrition.application.ManageMealPlans;
import com.fithub.nutrition.domain.plan.MealPlanNotFoundException;
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

/** As versões publicadas do plano alimentar que o paciente recebeu (DEV-0083, FR-46). O rascunho nunca aparece. */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Meu plano alimentar", description = "O plano alimentar que o paciente recebeu")
class MyMealPlanController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageMealPlans managePlans;

    MyMealPlanController(ManageMealPlans managePlans) {
        this.managePlans = managePlans;
    }

    @GetMapping(path = "/me/meal-plans/{planId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listMyMealPlanVersions",
            summary = "As versões publicadas do plano alimentar que o paciente recebeu",
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
                        description = "O plano não existe, é de outro paciente ou ainda é rascunho.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<MealPlanController.VersionSummaryResponse> versions(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return managePlans.clientVersions(jwt.getSubject(), planId).stream()
                .map(MealPlanController.VersionSummaryResponse::of)
                .toList();
    }

    @GetMapping(path = "/me/meal-plans/{planId}/versions/{number}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getMyMealPlanVersion",
            summary = "Uma versão publicada do plano alimentar que o paciente recebeu, com os macros",
            responses = {
                @ApiResponse(responseCode = "200", description = "A versão, com as refeições e os macros."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O plano ou a versão não existe, é de outro paciente ou ainda é rascunho.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    MealPlanController.PlanResponse version(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable int number) {
        return MealPlanController.PlanResponse.of(managePlans.clientVersion(jwt.getSubject(), planId, number));
    }

    @ExceptionHandler(MealPlanNotFoundException.class)
    ProblemDetail notFound(MealPlanNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }
}
