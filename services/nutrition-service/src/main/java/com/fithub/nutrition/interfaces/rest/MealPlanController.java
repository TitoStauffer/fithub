package com.fithub.nutrition.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.nutrition.application.ManageMealPlans;
import com.fithub.nutrition.domain.access.ClientNotFoundException;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.plan.InvalidMealPlanException;
import com.fithub.nutrition.domain.plan.MealPlan;
import com.fithub.nutrition.domain.plan.MealPlanNotFoundException;
import com.fithub.nutrition.domain.plan.MealPlanStateException;
import com.fithub.nutrition.domain.plan.MealPlanVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planos alimentares da nutricionista (DEV-0081, FR-42, FR-43, FR-45). Sem vínculo ativo, 404 (BR-02); o personal não
 * tem rota (BR-03); o plano de outra nutricionista responde como inexistente.
 */
@RestController
@PreAuthorize("hasRole('NUTRITIONIST')")
@Tag(name = "Planos alimentares", description = "Plano alimentar do paciente, com refeições, alimentos e macros")
class MealPlanController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ManageMealPlans managePlans;

    MealPlanController(ManageMealPlans managePlans) {
        this.managePlans = managePlans;
    }

    @Schema(name = "NutrientAmounts", description = "Energia e nutrientes de uma porção, com 1 casa decimal.")
    record AmountsResponse(
            BigDecimal energyKcal,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatG,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Ausente quando algum alimento da soma não traz a fibra.")
            BigDecimal fiberG) {

        static AmountsResponse of(Food.Amounts amounts) {
            return new AmountsResponse(
                    amounts.energyKcal(), amounts.proteinG(), amounts.carbsG(), amounts.fatG(), amounts.fiberG());
        }
    }

    @Schema(name = "MealItem", description = "Alimento da refeição, com a quantidade e os macros dela.")
    record ItemResponse(
            UUID foodId,
            String foodName,

            @Schema(description = "G (gramas) ou ML (mililitros).")
            Food.BaseUnit baseUnit,

            @Schema(description = "Quantidade na unidade base.")
            BigDecimal amount,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "A medida caseira usada para exibir.")
            UUID measureId,

            @JsonInclude(JsonInclude.Include.NON_NULL) String measureName,

            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal measureCount,

            AmountsResponse nutrients,

            @Schema(description = "Até 2 trocas possíveis: a secundária e a terciária, na ordem.")
            List<AlternativeResponse> alternatives) {

        static ItemResponse of(ManageMealPlans.ItemView view) {
            return new ItemResponse(
                    view.food().id(),
                    view.food().name(),
                    view.food().baseUnit(),
                    view.item().amount(),
                    view.measure() == null ? null : view.measure().id(),
                    view.measure() == null ? null : view.measure().name(),
                    view.measure() == null ? null : view.item().measureCount(),
                    AmountsResponse.of(view.nutrients()),
                    view.alternatives().stream().map(AlternativeResponse::of).toList());
        }
    }

    @Schema(name = "MealItemAlternative", description = "Troca possível do alimento, com a diferença para o principal.")
    record AlternativeResponse(
            UUID foodId,
            String foodName,

            @Schema(description = "G (gramas) ou ML (mililitros).")
            Food.BaseUnit baseUnit,

            @Schema(description = "Quantidade na unidade base.")
            BigDecimal amount,

            AmountsResponse nutrients,

            @Schema(description = "Alternativa menos principal, com sinal: positivo é a mais na alternativa.")
            AmountsResponse difference) {

        static AlternativeResponse of(ManageMealPlans.AlternativeView view) {
            return new AlternativeResponse(
                    view.food().id(),
                    view.food().name(),
                    view.food().baseUnit(),
                    view.alternative().amount(),
                    AmountsResponse.of(view.nutrients()),
                    AmountsResponse.of(view.difference()));
        }
    }

    @Schema(name = "Meal", description = "Refeição do plano, com os alimentos e a soma dos macros.")
    record MealResponse(
            int position,
            String name,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Horário sugerido (HH:MM).")
            String suggestedTime,

            @JsonInclude(JsonInclude.Include.NON_NULL) String note,

            List<ItemResponse> items,
            AmountsResponse total) {

        static MealResponse of(ManageMealPlans.MealView view) {
            return new MealResponse(
                    view.meal().position(),
                    view.meal().name(),
                    view.meal().suggestedTime() == null
                            ? null
                            : view.meal().suggestedTime().format(TIME),
                    view.meal().note(),
                    view.items().stream().map(ItemResponse::of).toList(),
                    AmountsResponse.of(view.total()));
        }
    }

    @Schema(name = "MealPlan", description = "Plano alimentar, com as refeições e os macros do dia.")
    record PlanResponse(
            UUID id,
            UUID clientId,

            @Schema(description = "DRAFT, PUBLISHED (o ativo) ou REPLACED.")
            MealPlan.Status status,

            int version,
            String name,
            LocalDate startsOn,

            @JsonInclude(JsonInclude.Include.NON_NULL) String note,

            @JsonInclude(JsonInclude.Include.NON_NULL) Instant publishedAt,

            List<MealResponse> meals,

            @Schema(description = "A soma das refeições.") AmountsResponse day,

            Instant createdAt,
            Instant updatedAt) {

        static PlanResponse of(ManageMealPlans.PlanView view) {
            MealPlan plan = view.plan();
            MealPlanVersion current = plan.current();
            return new PlanResponse(
                    plan.id(),
                    plan.clientId(),
                    plan.status(),
                    current.number(),
                    current.name(),
                    current.startsOn(),
                    current.note(),
                    current.publishedAt(),
                    view.meals().stream().map(MealResponse::of).toList(),
                    AmountsResponse.of(view.day()),
                    plan.createdAt(),
                    plan.updatedAt());
        }
    }

    @Schema(
            name = "MealItemInput",
            description =
                    "O alimento e a quantidade (0,1 a 5000), ou a medida caseira e quantas (a quantidade vem dela).")
    record ItemRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID foodId,

            BigDecimal amount,
            UUID measureId,
            BigDecimal measureCount,

            @Schema(description = "Até 2 alternativas, sem repetir o alimento principal nem a outra.")
            List<AlternativeRequest> alternatives) {}

    @Schema(name = "MealItemAlternativeInput", description = "O alimento da alternativa e a quantidade (0,1 a 5000).")
    record AlternativeRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID foodId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal amount) {}

    @Schema(name = "MealInput", description = "Nome (2 a 60), horário opcional (HH:MM), observação e até 30 alimentos.")
    record MealRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(description = "HH:MM", example = "07:30")
            String suggestedTime,

            String note,
            List<ItemRequest> items) {}

    @Schema(name = "MealPlanInput", description = "Nome (2 a 80), início, observação (até 500) e de 1 a 10 refeições.")
    record PlanRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate startsOn,

            String note,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<MealRequest> meals) {

        ManageMealPlans.Draft toDraft() {
            if (meals == null) {
                return new ManageMealPlans.Draft(name, startsOn, note, null);
            }
            List<ManageMealPlans.MealDraft> drafts = new ArrayList<>();
            for (int index = 0; index < meals.size(); index++) {
                MealRequest meal = meals.get(index);
                drafts.add(meal == null ? null : mealDraft(meal, index));
            }
            return new ManageMealPlans.Draft(name, startsOn, note, drafts);
        }

        private static ManageMealPlans.MealDraft mealDraft(MealRequest meal, int index) {
            List<ManageMealPlans.ItemDraft> items = meal.items() == null
                    ? null
                    : meal.items().stream()
                            .map(item -> item == null
                                    ? null
                                    : new ManageMealPlans.ItemDraft(
                                            item.foodId(),
                                            item.amount(),
                                            item.measureId(),
                                            item.measureCount(),
                                            alternatives(item.alternatives())))
                            .toList();
            return new ManageMealPlans.MealDraft(meal.name(), time(meal.suggestedTime(), index), meal.note(), items);
        }

        private static List<ManageMealPlans.AlternativeDraft> alternatives(List<AlternativeRequest> alternatives) {
            return alternatives == null
                    ? null
                    : alternatives.stream()
                            .map(alternative -> alternative == null
                                    ? null
                                    : new ManageMealPlans.AlternativeDraft(alternative.foodId(), alternative.amount()))
                            .toList();
        }

        /** O horário em HH:MM; vazio é sem horário. */
        private static LocalTime time(String value, int index) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalTime.parse(value.strip(), TIME);
            } catch (DateTimeParseException invalid) {
                throw new InvalidMealPlanException(
                        "meals[" + index + "].suggestedTime", "Informe o horário no formato HH:MM.");
            }
        }
    }

    @GetMapping(path = "/clients/{clientId}/meal-plans", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listMealPlans",
            summary = "Os planos alimentares que a nutricionista criou para o paciente vinculado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Planos, do mais novo para o mais antigo."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return managePlans.listForClient(jwt.getSubject(), clientId).stream()
                .map(PlanResponse::of)
                .toList();
    }

    @PostMapping(
            path = "/clients/{clientId}/meal-plans",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createMealPlan",
            summary = "Cria o plano alimentar do paciente vinculado, em rascunho",
            responses = {
                @ApiResponse(responseCode = "201", description = "Plano criado; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou alimento indisponível; o campo vem em errors.",
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
                        description = "Sem vínculo ativo com o paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<PlanResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @RequestBody PlanRequest request) {
        ManageMealPlans.PlanView plan = managePlans.create(jwt.getSubject(), clientId, request.toDraft());
        return ResponseEntity.created(URI.create("/meal-plans/" + plan.plan().id()))
                .body(PlanResponse.of(plan));
    }

    @GetMapping(path = "/meal-plans/{planId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getMealPlan",
            summary = "O plano alimentar, para a autora (mesmo depois do fim do vínculo)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Plano com as refeições e os macros."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O plano não existe ou é de outra nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanResponse view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return PlanResponse.of(managePlans.view(jwt.getSubject(), planId));
    }

    @PutMapping(
            path = "/meal-plans/{planId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateMealPlan",
            summary = "Edita o rascunho do plano alimentar",
            responses = {
                @ApiResponse(responseCode = "200", description = "Rascunho editado."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou alimento indisponível; o campo vem em errors.",
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
                        description = "O plano não existe, é de outra nutricionista ou o vínculo acabou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O plano já foi publicado.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @RequestBody PlanRequest request) {
        return PlanResponse.of(managePlans.update(jwt.getSubject(), planId, request.toDraft()));
    }

    @PostMapping(path = "/meal-plans/{planId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "publishMealPlan",
            summary = "Publica o rascunho; o plano ativo anterior do paciente fica substituído",
            responses = {
                @ApiResponse(responseCode = "200", description = "Plano publicado."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Alguma refeição sem alimento (BR-10); o campo vem em errors.",
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
                        description = "O plano não existe, é de outra nutricionista ou o vínculo acabou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O plano já foi publicado.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return PlanResponse.of(managePlans.publish(jwt.getSubject(), planId));
    }

    @ExceptionHandler(InvalidMealPlanException.class)
    ProblemDetail invalid(InvalidMealPlanException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler({ClientNotFoundException.class, MealPlanNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }

    @ExceptionHandler(MealPlanStateException.class)
    ProblemDetail conflict(MealPlanStateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflito");
        return problem;
    }
}
