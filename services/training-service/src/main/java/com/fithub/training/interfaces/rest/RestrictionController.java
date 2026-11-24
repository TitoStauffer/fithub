package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ManageRestrictions;
import com.fithub.training.domain.access.ClientNotFoundException;
import com.fithub.training.domain.restriction.BodyRegion;
import com.fithub.training.domain.restriction.InvalidRestrictionException;
import com.fithub.training.domain.restriction.MobilityRestriction;
import com.fithub.training.domain.restriction.RestrictionNotFoundException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restrições de mobilidade do aluno pelo personal vinculado (DEV-0058, FR-26). Sem vínculo ativo, 404, sem revelar
 * o aluno (BR-02).
 */
@RestController
@RequestMapping("/clients/{clientId}/restrictions")
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Restrições de mobilidade", description = "Restrições de mobilidade do aluno")
class RestrictionController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageRestrictions manageRestrictions;

    RestrictionController(ManageRestrictions manageRestrictions) {
        this.manageRestrictions = manageRestrictions;
    }

    @Schema(name = "MobilityRestriction", description = "Restrição de mobilidade do aluno.")
    record RestrictionResponse(UUID id, BodyRegion region, String description, Instant createdAt, Instant updatedAt) {

        static RestrictionResponse of(MobilityRestriction restriction) {
            return new RestrictionResponse(
                    restriction.id(),
                    restriction.region(),
                    restriction.description(),
                    restriction.createdAt(),
                    restriction.updatedAt());
        }
    }

    @Schema(name = "MobilityRestrictionInput", description = "Região do corpo e descrição da restrição.")
    record RestrictionRequest(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    implementation = BodyRegion.class,
                    description = "Região do corpo.")
            String region,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 1 a 500 caracteres.")
            String description) {

        /** A região chega como texto para o erro sair com o campo, em vez do 400 genérico de leitura do corpo. */
        BodyRegion bodyRegion() {
            if (region == null || region.isBlank()) {
                return null;
            }
            try {
                return BodyRegion.valueOf(region.strip());
            } catch (IllegalArgumentException unknown) {
                throw new InvalidRestrictionException("region", "Região fora da lista.");
            }
        }
    }

    private static final String NOT_FOUND = "Sem vínculo ativo com o aluno, ou a restrição não existe ou é de outro.";

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Restrições de mobilidade do aluno vinculado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Restrições, da mais antiga para a mais nova."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o aluno.",
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Registra uma restrição de mobilidade do aluno vinculado",
            responses = {
                @ApiResponse(responseCode = "201", description = "Restrição registrada; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Região fora da lista ou descrição fora do tamanho; o campo vem em errors.",
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
                        description = "Sem vínculo ativo com o aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<RestrictionResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @RequestBody RestrictionRequest request) {
        MobilityRestriction restriction =
                manageRestrictions.create(jwt.getSubject(), clientId, request.bodyRegion(), request.description());
        return ResponseEntity.created(URI.create("/clients/" + clientId + "/restrictions/" + restriction.id()))
                .body(RestrictionResponse.of(restriction));
    }

    @PutMapping(
            path = "/{restrictionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Edita uma restrição de mobilidade do aluno vinculado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Restrição editada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Região fora da lista ou descrição fora do tamanho; o campo vem em errors.",
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
                        description = NOT_FOUND,
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
        return RestrictionResponse.of(manageRestrictions.update(
                jwt.getSubject(), clientId, restrictionId, request.bodyRegion(), request.description()));
    }

    @DeleteMapping(path = "/{restrictionId}")
    @Operation(
            summary = "Remove uma restrição de mobilidade do aluno vinculado",
            responses = {
                @ApiResponse(responseCode = "204", description = "Restrição removida.", content = @Content()),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = NOT_FOUND,
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

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail clientNotFound(ClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Cliente não encontrado");
        return problem;
    }

    @ExceptionHandler(RestrictionNotFoundException.class)
    ProblemDetail restrictionNotFound(RestrictionNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Restrição não encontrada");
        return problem;
    }
}
