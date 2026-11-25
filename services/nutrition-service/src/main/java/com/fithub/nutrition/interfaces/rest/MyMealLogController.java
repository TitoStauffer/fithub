package com.fithub.nutrition.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.nutrition.application.LogMeals;
import com.fithub.nutrition.application.MealLogPhotos;
import com.fithub.nutrition.domain.log.InvalidMealLogException;
import com.fithub.nutrition.domain.log.MealLog;
import com.fithub.nutrition.domain.log.MealLogConflictException;
import com.fithub.nutrition.domain.log.MealLogNotFoundException;
import com.fithub.nutrition.domain.plan.MealPlanNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
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

/** Refeições de hoje e o registro delas pelo paciente (DEV-0087, FR-50). */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Registro de refeições", description = "As refeições de hoje e o registro delas pelo paciente")
class MyMealLogController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final LogMeals logMeals;
    private final MealLogPhotos photos;

    MyMealLogController(LogMeals logMeals, MealLogPhotos photos) {
        this.logMeals = logMeals;
        this.photos = photos;
    }

    @Schema(name = "MealLogSubstitution", description = "A alternativa usada num alimento da refeição.")
    record SubstitutionBody(
            @Schema(description = "A posição do alimento na refeição.")
            Integer itemPosition,

            @Schema(description = "0 para a secundária, 1 para a terciária.")
            Integer alternative) {

        MealLog.Substitution toSubstitution() {
            return new MealLog.Substitution(itemPosition, alternative);
        }
    }

    @Schema(name = "MealLog", description = "Registro de uma refeição do plano num dia.")
    record LogResponse(
            UUID id,
            UUID planId,

            @Schema(description = "A versão do plano em que o registro foi feito.")
            int version,

            int mealPosition,

            @Schema(description = "O dia do registro (America/Sao_Paulo).")
            LocalDate loggedOn,

            @Schema(description = "DONE (feita), PARTIAL (parcial) ou SKIPPED (não feita).")
            MealLog.Status status,

            @Schema(description = "As alternativas usadas; o alimento ausente aqui foi o principal.")
            List<SubstitutionBody> substitutions,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "O desvio em texto livre.")
            String deviation,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "URL temporária (5 minutos) para ver a foto do prato.")
            URI photoUrl,

            @JsonInclude(JsonInclude.Include.NON_NULL) Instant photoUrlExpiresAt,

            Instant createdAt,
            Instant updatedAt) {

        static LogResponse of(MealLogPhotos.LogView view) {
            MealLog log = view.log();
            return new LogResponse(
                    log.id(),
                    log.planId(),
                    log.versionNumber(),
                    log.mealPosition(),
                    log.loggedOn(),
                    log.status(),
                    log.substitutions().stream()
                            .map(substitution ->
                                    new SubstitutionBody(substitution.itemPosition(), substitution.alternative()))
                            .toList(),
                    log.deviation(),
                    view.photoUrl(),
                    view.photoUrlExpiresAt(),
                    log.createdAt(),
                    log.updatedAt());
        }
    }

    @Schema(name = "MealLogInput", description = "Registro ou correção da refeição de hoje.")
    record LogRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID planId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Integer mealPosition,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            MealLog.Status status,

            @Schema(description = "Só na refeição feita ou parcial.")
            List<SubstitutionBody> substitutions,

            @Schema(description = "O desvio em texto livre, até 500 caracteres.")
            String deviation) {

        LogMeals.LogDraft toDraft() {
            return new LogMeals.LogDraft(
                    planId,
                    mealPosition,
                    status,
                    substitutions == null
                            ? null
                            : substitutions.stream()
                                    .map(body -> body == null ? null : body.toSubstitution())
                                    .toList(),
                    deviation);
        }
    }

    @Schema(name = "MealPhotoUploadRequest", description = "O tipo da foto: image/jpeg ou image/png.")
    record UploadRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String contentType) {}

    @Schema(name = "MealPhotoUpload", description = "URL pré-assinada (5 minutos) para enviar a foto e o id do envio.")
    record UploadResponse(URI uploadUrl, String uploadId, Instant expiresAt) {}

    @Schema(name = "MealPhotoConfirmation", description = "O id do envio que a URL pré-assinada recebeu.")
    record ConfirmRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String uploadId) {}

    @Schema(name = "MealPlanToday", description = "O plano publicado do paciente e os registros de hoje.")
    record TodayResponse(MealPlanController.PlanResponse plan, List<LogResponse> logs) {}

    @GetMapping(path = "/me/meal-plan/today", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getMyMealPlanToday",
            summary = "As refeições de hoje: o plano publicado do paciente, com os macros, e os registros do dia",
            responses = {
                @ApiResponse(responseCode = "200", description = "O plano e os registros de hoje."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O paciente não tem plano alimentar publicado.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    TodayResponse today(@AuthenticationPrincipal Jwt jwt) {
        LogMeals.Today today = logMeals.today(jwt.getSubject());
        return new TodayResponse(
                MealPlanController.PlanResponse.of(today.plan()),
                today.logs().stream().map(LogResponse::of).toList());
    }

    @PutMapping(
            path = "/me/meal-logs/{logId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "logMyMeal",
            summary = "Registra a refeição de hoje; repetir com o mesmo id corrige o registro",
            responses = {
                @ApiResponse(responseCode = "200", description = "Registro gravado."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Refeição, alimento ou alternativa inexistentes, ou troca na refeição não feita.",
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
                        description = "O plano não existe, é de outro paciente ou ainda é rascunho.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "O dia já passou, a refeição já foi registrada hoje, o id é de outro registro ou o plano"
                                        + " foi substituído.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    LogResponse log(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID logId, @RequestBody LogRequest request) {
        return LogResponse.of(logMeals.record(jwt.getSubject(), logId, request.toDraft()));
    }

    @DeleteMapping(path = "/me/meal-logs/{logId}")
    @Operation(
            operationId = "removeMyMealLog",
            summary = "Apaga o registro da refeição, só no dia em que foi feito",
            responses = {
                @ApiResponse(responseCode = "204", description = "Registro apagado.", content = @Content()),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O registro não existe ou é de outro paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O dia do registro já passou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID logId) {
        logMeals.remove(jwt.getSubject(), logId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            path = "/me/meal-logs/{logId}/photo/upload-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "requestMyMealPhotoUpload",
            summary = "URL pré-assinada para enviar a foto do prato (JPEG ou PNG, até 10 MB)",
            responses = {
                @ApiResponse(responseCode = "200", description = "URL de envio e o id do envio."),
                @ApiResponse(
                        responseCode = "400",
                        description = "O tipo não é JPEG nem PNG.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O registro não existe ou é de outro paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O dia do registro já passou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    UploadResponse requestUpload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID logId, @RequestBody UploadRequest request) {
        MealLogPhotos.Upload upload = photos.requestUpload(jwt.getSubject(), logId, request.contentType());
        return new UploadResponse(upload.uploadUrl(), upload.uploadId(), upload.expiresAt());
    }

    @PutMapping(
            path = "/me/meal-logs/{logId}/photo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "confirmMyMealPhoto",
            summary = "Confirma a foto do prato enviada; a anterior é substituída",
            responses = {
                @ApiResponse(responseCode = "200", description = "Registro com a URL de leitura da foto."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Envio inexistente ou a foto não é JPEG/PNG de até 10 MB.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "O registro não existe ou é de outro paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O dia do registro já passou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    LogResponse confirmPhoto(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID logId, @RequestBody ConfirmRequest request) {
        return LogResponse.of(photos.confirm(jwt.getSubject(), logId, request.uploadId()));
    }

    @DeleteMapping(path = "/me/meal-logs/{logId}/photo", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "removeMyMealPhoto",
            summary = "Tira a foto do prato do registro",
            responses = {
                @ApiResponse(responseCode = "200", description = "Registro sem a foto."),
                @ApiResponse(
                        responseCode = "404",
                        description = "O registro não existe ou é de outro paciente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O dia do registro já passou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    LogResponse removePhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID logId) {
        return LogResponse.of(photos.remove(jwt.getSubject(), logId));
    }

    @ExceptionHandler(InvalidMealLogException.class)
    ProblemDetail invalid(InvalidMealLogException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler({MealPlanNotFoundException.class, MealLogNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }

    @ExceptionHandler(MealLogConflictException.class)
    ProblemDetail conflict(MealLogConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflito");
        return problem;
    }
}
