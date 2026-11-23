package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageClientProfile;
import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.client.domain.profile.InvalidProfileException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil do cliente da conta autenticada (DEV-0032). Só existe "o meu" perfil: a conta vem sempre do
 * {@code sub} do token. A leitura pelo profissional vinculado é a DEV-0040.
 */
@RestController
@RequestMapping("/clients/me/profile")
@Tag(name = "Perfil do cliente", description = "Perfil do cliente da conta autenticada")
class ClientProfileController {

    private final ManageClientProfile manageClientProfile;

    ClientProfileController(ManageClientProfile manageClientProfile) {
        this.manageClientProfile = manageClientProfile;
    }

    @Schema(name = "ClientProfileRequest")
    record ProfileRequest(
            @NotBlank(message = "Informe seu nome.") String name,

            @NotNull(message = "Informe sua data de nascimento.")
            LocalDate birthDate,

            @NotNull(message = "Informe o gênero (ou prefira não informar).")
            ClientProfile.Gender gender,

            @NotNull(message = "Informe sua altura em centímetros.")
            Integer heightCm,

            @NotNull(message = "Informe seu objetivo.") ClientProfile.Goal goal) {}

    @Schema(name = "ClientProfile")
    record ProfileResponse(
            String name,
            LocalDate birthDate,

            @Schema(description = "Idade em anos completos, calculada a partir da data de nascimento.")
            int age,

            ClientProfile.Gender gender,
            int heightCm,
            ClientProfile.Goal goal) {

        static ProfileResponse of(ManageClientProfile.Result result) {
            ClientProfile profile = result.profile();
            return new ProfileResponse(
                    profile.name(),
                    profile.birthDate(),
                    result.age(),
                    profile.gender(),
                    profile.heightCm(),
                    profile.goal());
        }
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cria ou atualiza o perfil da conta autenticada",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Perfil criado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProfileResponse.class))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Perfil atualizado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProfileResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo inválido; errors[] traz o campo e a mensagem.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<ProfileResponse> save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        var result = manageClientProfile.save(new ManageClientProfile.Command(
                jwt.getSubject(),
                request.name(),
                request.birthDate(),
                request.gender(),
                request.heightCm(),
                request.goal()));
        var body = ProfileResponse.of(result);
        return result.created() ? ResponseEntity.status(HttpStatus.CREATED).body(body) : ResponseEntity.ok(body);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Perfil da conta autenticada",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Perfil da conta autenticada.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProfileResponse.class))),
                @ApiResponse(responseCode = "404", description = "A conta ainda não tem perfil."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<ProfileResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return manageClientProfile
                .findMine(jwt.getSubject())
                .map(result -> ResponseEntity.ok(ProfileResponse.of(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(InvalidProfileException.class)
    ProblemDetail invalidProfile(InvalidProfileException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }
}
