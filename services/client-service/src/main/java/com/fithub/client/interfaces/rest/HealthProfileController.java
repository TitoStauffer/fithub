package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageHealthProfile;
import com.fithub.client.domain.profile.HealthProfile;
import com.fithub.client.domain.profile.InvalidProfileException;
import com.fithub.client.domain.profile.ProfileRequiredException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil de saúde da conta autenticada (DEV-0033). Dado sensível: só "o meu", com a conta sempre do
 * {@code sub} do token, e nada dele em log ou mensagem de erro. A leitura pelo profissional vinculado é
 * a DEV-0040.
 */
@RestController
@RequestMapping("/clients/me/health")
@Tag(name = "Perfil de saúde", description = "Perfil de saúde do cliente da conta autenticada")
class HealthProfileController {

    private final ManageHealthProfile manageHealthProfile;

    HealthProfileController(ManageHealthProfile manageHealthProfile) {
        this.manageHealthProfile = manageHealthProfile;
    }

    @Schema(name = "HealthProfileRequest")
    record HealthRequest(
            @NotNull(message = "Informe as condições (lista vazia se não houver nenhuma).")
            @ArraySchema(
                    uniqueItems = true,
                    arraySchema = @Schema(description = "Condições marcadas; repetidas contam uma vez."))
            List<HealthProfile.Condition> conditions,

            @Schema(description = "Texto livre opcional, até 1000 caracteres.", maxLength = HealthProfile.MAX_NOTES)
            String notes) {}

    @Schema(name = "HealthProfile")
    record HealthResponse(
            @ArraySchema(uniqueItems = true) List<HealthProfile.Condition> conditions,
            @Schema(nullable = true) String notes,
            Instant updatedAt) {

        static HealthResponse of(HealthProfile profile) {
            // EnumSet: a ordem é a da declaração das condições, estável entre leituras.
            return new HealthResponse(List.copyOf(profile.conditions()), profile.notes(), profile.updatedAt());
        }
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Grava o perfil de saúde da conta autenticada (substitui o anterior)",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Perfil de saúde gravado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = HealthResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo inválido; errors[] traz o campo e a mensagem.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "A conta ainda não tem o perfil do cliente.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    HealthResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HealthRequest request) {
        return HealthResponse.of(manageHealthProfile.save(
                new ManageHealthProfile.Command(jwt.getSubject(), request.conditions(), request.notes())));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Perfil de saúde da conta autenticada",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Perfil de saúde da conta autenticada.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = HealthResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A conta ainda não informou os dados de saúde.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<HealthResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return manageHealthProfile
                .findMine(jwt.getSubject())
                .map(profile -> ResponseEntity.ok(HealthResponse.of(profile)))
                .orElseThrow(HealthProfileController::noHealthProfile);
    }

    /** 404 em Problem Details, como os outros erros (aprendizado contratos-api.md). */
    private static ErrorResponseException noHealthProfile() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Esta conta ainda não informou os dados de saúde.");
        problem.setTitle("Perfil de saúde não encontrado");
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }

    @ExceptionHandler(InvalidProfileException.class)
    ProblemDetail invalidHealth(InvalidProfileException exception) {
        return ProfileProblems.invalid(exception);
    }

    @ExceptionHandler(ProfileRequiredException.class)
    ProblemDetail profileRequired(ProfileRequiredException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Perfil ainda não criado");
        return problem;
    }
}
