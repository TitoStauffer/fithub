package com.fithub.account.interfaces.rest;

import com.fithub.account.application.RegisterProfessional;
import com.fithub.account.domain.professional.InvalidRegistrationException;
import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.Registration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro do profissional da conta autenticada (DEV-0029). Só existe "o meu" cadastro: a conta vem
 * sempre do {@code sub} do token, e não há leitura do cadastro de outra pessoa.
 */
@RestController
@RequestMapping("/professionals/me")
@Tag(name = "Profissional", description = "Cadastro do profissional da conta autenticada")
class ProfessionalController {

    private final RegisterProfessional registerProfessional;

    ProfessionalController(RegisterProfessional registerProfessional) {
        this.registerProfessional = registerProfessional;
    }

    @Schema(name = "ProfessionalRegistrationRequest")
    record RegistrationRequest(
            @NotNull(message = "Informe se você é personal ou nutricionista.")
            Professional.Type type,

            @NotBlank(message = "Informe o número do registro.")
            String registrationNumber,

            @Schema(description = "Categoria do CREF (G ou P). Não se aplica ao CRN.", nullable = true)
            String registrationCategory,

            @NotBlank(message = "Informe a UF do registro.") String registrationState) {}

    @Schema(name = "ProfessionalRegistration")
    record RegistrationView(
            Registration.Council council, String number, String category, String state, String display) {}

    @Schema(name = "Professional")
    record ProfessionalResponse(
            UUID id,
            String name,
            Professional.Type type,
            RegistrationView registration,
            Professional.Situation situation,
            UUID organizationId,

            @Schema(description = "PENDING enquanto o papel de profissional não chega ao token; renove a sessão.")
            Professional.RoleStatus roleStatus,

            Instant createdAt) {

        static ProfessionalResponse of(Professional professional) {
            Registration registration = professional.registration();
            return new ProfessionalResponse(
                    professional.id(),
                    professional.name(),
                    professional.type(),
                    new RegistrationView(
                            registration.council(),
                            registration.number(),
                            registration.category(),
                            registration.state(),
                            registration.display()),
                    professional.situation(),
                    professional.organizationId(),
                    professional.roleStatus(),
                    professional.createdAt());
        }
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cadastra a conta autenticada como profissional",
            description = "201 quando cria; 200 quando o mesmo cadastro já existia; 409 quando a conta já tem"
                    + " um cadastro diferente.",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Cadastro criado.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProfessionalResponse.class))),
                @ApiResponse(
                        responseCode = "200",
                        description = "O mesmo cadastro já existia.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProfessionalResponse.class))),
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
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "A conta já tem um cadastro com outros dados.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<ProfessionalResponse> register(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegistrationRequest request) {
        var result = registerProfessional.register(new RegisterProfessional.Command(
                jwt.getSubject(),
                displayName(jwt),
                request.type(),
                request.registrationNumber(),
                request.registrationCategory(),
                request.registrationState()));
        var body = ProfessionalResponse.of(result.professional());
        return result.created()
                ? ResponseEntity.created(URI.create("/professionals/me")).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cadastro de profissional da conta autenticada",
            description = "404 se ainda não houver.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Cadastro da conta autenticada.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProfessionalResponse.class))),
                @ApiResponse(responseCode = "404", description = "A conta ainda não tem cadastro de profissional."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<ProfessionalResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return registerProfessional
                .findMine(jwt.getSubject())
                .map(professional -> ResponseEntity.ok(ProfessionalResponse.of(professional)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Nome do perfil do Keycloak: a claim {@code name} ou, sem ela, nome e sobrenome. O cadastro do
     * Keycloak exige os dois, então a falta só acontece com conta criada fora do fluxo normal.
     */
    static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        return ((given == null ? "" : given) + " " + (family == null ? "" : family)).strip();
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    ProblemDetail invalidRegistration(InvalidRegistrationException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(RegisterProfessional.ConflictException.class)
    ProblemDetail conflict(RegisterProfessional.ConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Cadastro já existe");
        return problem;
    }
}
