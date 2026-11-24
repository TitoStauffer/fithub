package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ManageOwnPlans;
import com.fithub.training.domain.plan.InvalidPlanException;
import com.fithub.training.domain.plan.PlanConflictException;
import com.fithub.training.domain.plan.PlanNotFoundException;
import com.fithub.training.domain.plan.PlanStateException;
import com.fithub.training.domain.plan.WorkoutPlan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ficha própria do aluno (DEV-0069, FR-52): a mesma ficha do personal (divisões, rascunho, publicação, versões), com
 * exercícios só do catálogo e sem alerta de restrição (o alerta é para o personal, FR-27). Publicada, aparece em
 * {@code GET /me/plans/active} e aceita sessão e séries.
 */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Ficha própria", description = "Fichas que o aluno monta para si")
class OwnPlanController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageOwnPlans manageOwnPlans;

    OwnPlanController(ManageOwnPlans manageOwnPlans) {
        this.manageOwnPlans = manageOwnPlans;
    }

    /** A ficha própria nunca tem alerta de restrição. */
    private static PlanController.PlanResponse respond(WorkoutPlan plan) {
        return PlanController.PlanResponse.of(plan, List.of());
    }

    @PostMapping(
            path = "/me/own-plans",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createOwnPlan",
            summary = "Cria uma ficha própria em rascunho",
            responses = {
                @ApiResponse(responseCode = "201", description = "Ficha criada; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou exercício fora do catálogo; o campo vem em errors.",
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
    ResponseEntity<PlanController.PlanResponse> create(
            @AuthenticationPrincipal Jwt jwt, @RequestBody PlanController.PlanRequest request) {
        WorkoutPlan plan = manageOwnPlans.create(jwt.getSubject(), request.toDraft());
        return ResponseEntity.created(URI.create("/me/own-plans/" + plan.id())).body(respond(plan));
    }

    @GetMapping(path = "/me/own-plans", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listOwnPlans",
            summary = "As fichas próprias do aluno, da mais nova para a mais antiga",
            responses = {
                @ApiResponse(responseCode = "200", description = "Fichas (a lista pode ser vazia)."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<PlanController.PlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return manageOwnPlans.list(jwt.getSubject()).stream()
                .map(OwnPlanController::respond)
                .toList();
    }

    @GetMapping(path = "/me/own-plans/{planId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "viewOwnPlan",
            summary = "Uma ficha própria do aluno",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe, é do personal ou de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanController.PlanResponse view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return respond(manageOwnPlans.view(jwt.getSubject(), planId));
    }

    @PutMapping(
            path = "/me/own-plans/{planId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateOwnPlan",
            summary = "Edita a ficha própria: o rascunho no lugar; a publicada vira a versão seguinte",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha editada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou exercício fora do catálogo; o campo vem em errors.",
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
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe, é do personal ou de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "A ficha foi substituída, ou outra edição chegou antes (versão base desatualizada).",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanController.PlanResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID planId,
            @RequestBody PlanController.PlanRequest request) {
        return respond(manageOwnPlans.update(jwt.getSubject(), planId, request.toDraft()));
    }

    @PostMapping(path = "/me/own-plans/{planId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "publishOwnPlan",
            summary = "Publica a ficha própria; a própria que estava ativa fica substituída",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha publicada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Alguma divisão está sem exercício (BR-10); a divisão vem em errors.",
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
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe, é do personal ou de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "A ficha já foi publicada.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanController.PlanResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return respond(manageOwnPlans.publish(jwt.getSubject(), planId));
    }

    @ExceptionHandler(InvalidPlanException.class)
    ProblemDetail invalid(InvalidPlanException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(PlanNotFoundException.class)
    ProblemDetail planNotFound(PlanNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Ficha não encontrada");
        return problem;
    }

    @ExceptionHandler(PlanConflictException.class)
    ProblemDetail conflict(PlanConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Edição concorrente");
        return problem;
    }

    @ExceptionHandler(PlanStateException.class)
    ProblemDetail state(PlanStateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Operação fora do estado da ficha");
        return problem;
    }
}
