package com.fithub.nutrition.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.nutrition.application.ViewFood;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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

/** Alimentos vistos pela nutricionista (DEV-0074, FR-35): a Tabela TACO e os próprios dela (BR-13). */
@RestController
@RequestMapping("/foods")
@PreAuthorize("hasRole('NUTRITIONIST')")
@Tag(name = "Alimentos", description = "Base de alimentos da nutricionista: TACO e alimentos próprios")
class FoodController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ViewFood viewFood;

    FoodController(ViewFood viewFood) {
        this.viewFood = viewFood;
    }

    @Schema(name = "FoodSource", description = "Origem do item importado, para o crédito.")
    record SourceResponse(String name, String externalId) {}

    @Schema(name = "Food", description = "Alimento, com os valores por 100 da unidade base (100 g ou 100 ml).")
    record FoodResponse(
            UUID id,
            Food.Kind kind,
            String name,

            @Schema(description = "G (gramas) ou ML (mililitros).")
            Food.BaseUnit baseUnit,

            BigDecimal energyKcal,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatG,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Ausente quando a fonte não traz.")
            BigDecimal fiberG,

            boolean vegan,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Presente só no item importado.")
            SourceResponse source) {

        static FoodResponse of(Food food) {
            Food.Nutrients per100 = food.per100();
            return new FoodResponse(
                    food.id(),
                    food.kind(),
                    food.name(),
                    food.baseUnit(),
                    per100.energyKcal(),
                    per100.proteinG(),
                    per100.carbsG(),
                    per100.fatG(),
                    per100.fiberG(),
                    food.vegan(),
                    food.source() == null
                            ? null
                            : new SourceResponse(
                                    food.source().name(), food.source().externalId()));
        }
    }

    @GetMapping(path = "/{foodId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "viewFood",
            summary = "Um alimento da TACO ou próprio da nutricionista",
            responses = {
                @ApiResponse(responseCode = "200", description = "Alimento."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O alimento não existe ou é próprio de outra nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    FoodResponse view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID foodId) {
        return FoodResponse.of(viewFood.view(jwt.getSubject(), foodId));
    }

    @ExceptionHandler(FoodNotFoundException.class)
    ProblemDetail notFound(FoodNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Alimento não encontrado");
        return problem;
    }
}
