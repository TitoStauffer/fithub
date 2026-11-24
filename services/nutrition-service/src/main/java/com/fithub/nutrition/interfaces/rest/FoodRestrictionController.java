package com.fithub.nutrition.interfaces.rest;

import com.fithub.nutrition.application.ManageFoodRestrictions;
import com.fithub.nutrition.domain.access.ClientNotFoundException;
import com.fithub.nutrition.domain.restriction.FoodRestriction;
import com.fithub.nutrition.domain.restriction.InvalidRestrictionException;
import com.fithub.nutrition.domain.restriction.RestrictionNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Restrições alimentares e preferências do paciente pela nutricionista vinculada (DEV-0080, FR-40). Sem vínculo
 * ativo, 404 (BR-02); o personal não tem rota (BR-03).
 */
@RestController
@PreAuthorize("hasRole('NUTRITIONIST')")
@Tag(name = "Restrições alimentares", description = "Alergias, intolerâncias e preferências do paciente")
class FoodRestrictionController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageFoodRestrictions manageRestrictions;

    FoodRestrictionController(ManageFoodRestrictions manageRestrictions) {
        this.manageRestrictions = manageRestrictions;
    }

    @Schema(name = "FoodRestriction", description = "Restrição alimentar ou preferência do paciente.")
    record RestrictionResponse(
            UUID id,
            UUID clientId,

            @Schema(description = "ALLERGY e INTOLERANCE geram alerta no plano; PREFERENCE não.")
            FoodRestriction.Kind kind,

            String description,

            @Schema(description = "Termos procurados no nome dos alimentos.")
            List<String> terms,

            Instant createdAt,
            Instant updatedAt) {

        static RestrictionResponse of(FoodRestriction restriction) {
            return new RestrictionResponse(
                    restriction.id(),
                    restriction.clientId(),
                    restriction.kind(),
                    restriction.description(),
                    restriction.terms(),
                    restriction.createdAt(),
                    restriction.updatedAt());
        }
    }

    @Schema(name = "FoodRestrictionInput", description = "Tipo, descrição (2 a 200) e de 1 a 10 termos (2 a 40 cada).")
    record RestrictionRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            FoodRestriction.Kind kind,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String description,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> terms) {

        ManageFoodRestrictions.Draft toDraft() {
            return new ManageFoodRestrictions.Draft(kind, description, terms);
        }
    }

    @GetMapping(path = "/clients/{clientId}/food-restrictions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listFoodRestrictions",
            summary = "As restrições alimentares do paciente vinculado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Restrições (a lista pode ser vazia)."),
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
    List<RestrictionResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return manageRestrictions.list(jwt.getSubject(), clientId).stream()
                .map(RestrictionResponse::of)
                .toList();
    }

    @PostMapping(
            path = "/clients/{clientId}/food-restrictions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createFoodRestriction",
            summary = "Registra uma restrição alimentar ou preferência do paciente",
            responses = {
                @ApiResponse(responseCode = "201", description = "Restrição registrada; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras ou mais de 30 restrições; o campo vem em errors.",
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
    ResponseEntity<RestrictionResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @RequestBody RestrictionRequest request) {
        FoodRestriction restriction = manageRestrictions.create(jwt.getSubject(), clientId, request.toDraft());
        return ResponseEntity.created(URI.create("/clients/" + clientId + "/food-restrictions/" + restriction.id()))
                .body(RestrictionResponse.of(restriction));
    }

    @PutMapping(
            path = "/clients/{clientId}/food-restrictions/{restrictionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateFoodRestriction",
            summary = "Edita a restrição alimentar do paciente",
            responses = {
                @ApiResponse(responseCode = "200", description = "Restrição editada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras; o campo vem em errors.",
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
                        description = "Sem vínculo ativo, ou a restrição não existe ou é de outro paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    RestrictionResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID clientId,
            @PathVariable UUID restrictionId,
            @RequestBody RestrictionRequest request) {
        return RestrictionResponse.of(
                manageRestrictions.update(jwt.getSubject(), clientId, restrictionId, request.toDraft()));
    }

    @DeleteMapping(path = "/clients/{clientId}/food-restrictions/{restrictionId}")
    @Operation(
            operationId = "removeFoodRestriction",
            summary = "Apaga a restrição alimentar do paciente",
            responses = {
                @ApiResponse(responseCode = "204", description = "Restrição apagada.", content = @Content()),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é nutricionista.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo, ou a restrição não existe ou é de outro paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @PathVariable UUID restrictionId) {
        manageRestrictions.remove(jwt.getSubject(), clientId, restrictionId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidRestrictionException.class)
    ProblemDetail invalid(InvalidRestrictionException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler({ClientNotFoundException.class, RestrictionNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }
}
