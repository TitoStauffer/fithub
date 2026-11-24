package com.fithub.nutrition.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.nutrition.application.ManageFoodMeasures;
import com.fithub.nutrition.application.ManageOwnFoods;
import com.fithub.nutrition.application.ViewFood;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodMeasure;
import com.fithub.nutrition.domain.food.FoodMeasureNotFoundException;
import com.fithub.nutrition.domain.food.FoodNotFoundException;
import com.fithub.nutrition.domain.food.InvalidFoodException;
import com.fithub.nutrition.domain.food.Recipe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.net.URI;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alimentos vistos pela nutricionista (DEV-0074, FR-35): a Tabela TACO e os próprios dela (BR-13), com as medidas
 * caseiras dela (DEV-0076, FR-37).
 */
@RestController
@RequestMapping("/foods")
@PreAuthorize("hasRole('NUTRITIONIST')")
@Tag(name = "Alimentos", description = "Base de alimentos da nutricionista: TACO e alimentos próprios")
class FoodController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ViewFood viewFood;
    private final ManageFoodMeasures manageMeasures;
    private final ManageOwnFoods manageOwnFoods;

    FoodController(ViewFood viewFood, ManageFoodMeasures manageMeasures, ManageOwnFoods manageOwnFoods) {
        this.viewFood = viewFood;
        this.manageMeasures = manageMeasures;
        this.manageOwnFoods = manageOwnFoods;
    }

    @Schema(name = "FoodMeasure", description = "Medida caseira da nutricionista e quanto vale na unidade base.")
    record MeasureResponse(
            UUID id,
            String name,

            @Schema(description = "Gramas (ou ml) de uma medida.")
            BigDecimal amount) {

        static MeasureResponse of(FoodMeasure measure) {
            return new MeasureResponse(measure.id(), measure.name(), measure.amount());
        }
    }

    @Schema(name = "RecipeIngredient", description = "Ingrediente da receita: o alimento e a quantidade.")
    record IngredientBody(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID foodId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 0,1 a 5000 g (ou ml).")
            BigDecimal amount) {}

    @Schema(name = "Recipe", description = "Ingredientes e rendimento; os valores por 100 g saem deles.")
    record RecipeResponse(List<IngredientBody> ingredients, BigDecimal yieldGrams) {

        static RecipeResponse of(Recipe recipe) {
            return new RecipeResponse(
                    recipe.ingredients().stream()
                            .map(ingredient -> new IngredientBody(ingredient.foodId(), ingredient.amount()))
                            .toList(),
                    recipe.yieldGrams());
        }
    }

    @Schema(name = "RecipeInput", description = "Ingredientes (1 a 30) e o rendimento total.")
    record RecipeRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<IngredientBody> ingredients,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Em gramas, de 1 a 20000.")
            BigDecimal yieldGrams) {}

    @Schema(
            name = "FoodInput",
            description =
                    "Alimento próprio. Com recipe, os valores por 100 g e a etiqueta vegano saem dos ingredientes.")
    record FoodRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 2 a 120 caracteres.")
            String name,

            @Schema(description = "G ou ML; a receita é sempre em gramas.")
            Food.BaseUnit baseUnit,

            @Schema(description = "Por 100, de 0 a 900 kcal; ignorado na receita.")
            BigDecimal energyKcal,

            @Schema(description = "Por 100, de 0 a 100 g; ignorado na receita.")
            BigDecimal proteinG,

            @Schema(description = "Por 100, de 0 a 100 g; ignorado na receita.")
            BigDecimal carbsG,

            @Schema(description = "Por 100, de 0 a 100 g; ignorado na receita.")
            BigDecimal fatG,

            @Schema(description = "Por 100, opcional.") BigDecimal fiberG,

            @Schema(description = "Ignorado na receita: vegana só se todos os ingredientes forem.")
            Boolean vegan,

            RecipeRequest recipe) {

        ManageOwnFoods.Draft toDraft() {
            return new ManageOwnFoods.Draft(
                    name,
                    baseUnit,
                    energyKcal,
                    proteinG,
                    carbsG,
                    fatG,
                    fiberG,
                    vegan,
                    recipe == null
                            ? null
                            : new ManageOwnFoods.RecipeDraft(
                                    recipe.ingredients() == null
                                            ? null
                                            : recipe.ingredients().stream()
                                                    .map(each -> each == null
                                                            ? null
                                                            : new Recipe.Ingredient(each.foodId(), each.amount()))
                                                    .toList(),
                                    recipe.yieldGrams()));
        }
    }

    @Schema(name = "FoodMeasureInput", description = "Medida caseira nova.")
    record MeasureRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 2 a 40 caracteres.")
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 0,1 a 2000 g (ou ml).")
            BigDecimal amount) {}

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
            SourceResponse source,

            @Schema(description = "As medidas caseiras da nutricionista neste alimento (só as dela).")
            List<MeasureResponse> measures,

            @Schema(description = "Se a dona arquivou o próprio: sai da busca, mas continua nos planos.")
            boolean archived,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Presente só na receita.")
            RecipeResponse recipe) {

        static FoodResponse of(Food food, List<FoodMeasure> measures) {
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
                                    food.source().name(), food.source().externalId()),
                    measures.stream().map(MeasureResponse::of).toList(),
                    food.archived(),
                    food.isRecipe() ? RecipeResponse.of(food.recipe()) : null);
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
        return FoodResponse.of(viewFood.view(jwt.getSubject(), foodId), manageMeasures.list(jwt.getSubject(), foodId));
    }

    @PostMapping(
            path = "/{foodId}/measures",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "addFoodMeasure",
            summary = "Cadastra uma medida caseira da nutricionista no alimento",
            responses = {
                @ApiResponse(responseCode = "201", description = "Medida cadastrada; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Nome ou valor fora das regras, nome repetido ou mais de 10 medidas; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
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
    ResponseEntity<MeasureResponse> addMeasure(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID foodId, @RequestBody MeasureRequest request) {
        FoodMeasure measure = manageMeasures.add(jwt.getSubject(), foodId, request.name(), request.amount());
        return ResponseEntity.created(URI.create("/foods/" + foodId + "/measures/" + measure.id()))
                .body(MeasureResponse.of(measure));
    }

    @DeleteMapping(path = "/{foodId}/measures/{measureId}")
    @Operation(
            operationId = "removeFoodMeasure",
            summary = "Apaga uma medida caseira da nutricionista",
            responses = {
                @ApiResponse(responseCode = "204", description = "Medida apagada.", content = @Content()),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A medida não existe, é de outro alimento ou de outra nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<Void> removeMeasure(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID foodId, @PathVariable UUID measureId) {
        manageMeasures.remove(jwt.getSubject(), foodId, measureId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createOwnFood",
            summary = "Cadastra um alimento ou receita própria da nutricionista",
            responses = {
                @ApiResponse(responseCode = "201", description = "Alimento cadastrado; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou ingrediente indisponível; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<FoodResponse> create(@AuthenticationPrincipal Jwt jwt, @RequestBody FoodRequest request) {
        Food food = manageOwnFoods.create(jwt.getSubject(), request.toDraft());
        return ResponseEntity.created(URI.create("/foods/" + food.id())).body(FoodResponse.of(food, List.of()));
    }

    @PutMapping(
            path = "/{foodId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateOwnFood",
            summary = "Edita o alimento ou a receita própria (a receita é recalculada)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Alimento editado."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou ingrediente indisponível; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O alimento não existe, é da TACO ou de outra nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    FoodResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID foodId, @RequestBody FoodRequest request) {
        Food food = manageOwnFoods.update(jwt.getSubject(), foodId, request.toDraft());
        return FoodResponse.of(food, manageMeasures.list(jwt.getSubject(), foodId));
    }

    @PostMapping(path = "/{foodId}/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "archiveOwnFood",
            summary = "Arquiva o alimento próprio: sai da busca, mas continua nos planos",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Alimento arquivado (arquivar de novo não muda nada)."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O alimento não existe, é da TACO ou de outra nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    FoodResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID foodId) {
        Food food = manageOwnFoods.archive(jwt.getSubject(), foodId);
        return FoodResponse.of(food, manageMeasures.list(jwt.getSubject(), foodId));
    }

    @ExceptionHandler(InvalidFoodException.class)
    ProblemDetail invalid(InvalidFoodException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(FoodMeasureNotFoundException.class)
    ProblemDetail measureNotFound(FoodMeasureNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Medida não encontrada");
        return problem;
    }

    @ExceptionHandler(FoodNotFoundException.class)
    ProblemDetail notFound(FoodNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Alimento não encontrado");
        return problem;
    }
}
