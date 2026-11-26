package com.fithub.nutrition.interfaces.rest;

import com.fithub.nutrition.application.ExportNutritionData;
import com.fithub.nutrition.domain.access.ProfessionalAccessAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação dos dados do cliente no nutrition (DEV-0167, NFR-01, LGPD art. 18, II e V). A conta vem sempre do token;
 * a foto do prato vem por URL temporária de 5 minutos e nunca passa pelo serviço.
 */
@RestController
@Tag(name = "Exportação", description = "Exportação dos dados do cliente autenticado")
class NutritionExportController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ExportNutritionData exportNutritionData;

    NutritionExportController(ExportNutritionData exportNutritionData) {
        this.exportNutritionData = exportNutritionData;
    }

    @Schema(
            name = "NutritionExportAccess",
            description = "Leitura de dado sensível pela nutricionista, sem o conteúdo.")
    record AccessExport(UUID professionalId, ProfessionalAccessAudit.Resource resource, Instant accessedAt) {

        static AccessExport of(ProfessionalAccessAudit.AccessRecord access) {
            return new AccessExport(access.professionalId(), access.resource(), access.accessedAt());
        }
    }

    @Schema(name = "NutritionExport", description = "Todos os dados que o nutrition tem do cliente.")
    record ExportResponse(
            Instant exportedAt,

            @Schema(description = "Cada versão recebida de cada plano alimentar, com refeições, alimentos e macros.")
            List<MealPlanController.PlanResponse> planVersions,

            @Schema(description = "Registros de refeição, com a foto do prato por URL temporária quando houver.")
            List<MyMealLogController.LogResponse> logs,

            List<FoodRestrictionController.RestrictionResponse> restrictions,

            @Schema(description = "Leituras das restrições alimentares pela nutricionista (DEV-0171), sem o conteúdo.")
            List<AccessExport> accessLog) {

        static ExportResponse of(ExportNutritionData.Export export) {
            return new ExportResponse(
                    export.exportedAt(),
                    export.planVersions().stream()
                            .map(MealPlanController.PlanResponse::of)
                            .toList(),
                    export.logs().stream()
                            .map(MyMealLogController.LogResponse::of)
                            .toList(),
                    export.restrictions().stream()
                            .map(FoodRestrictionController.RestrictionResponse::of)
                            .toList(),
                    export.accessLog().stream().map(AccessExport::of).toList());
        }
    }

    @GetMapping(path = "/me/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exporta todos os dados que o nutrition tem do cliente",
            description = "Planos alimentares recebidos (cada versão), registros de refeição com trocas, desvios e a"
                    + " foto do prato (URL temporária de 5 minutos), restrições alimentares e as leituras delas pela"
                    + " nutricionista (accessLog), sem o conteúdo. O app junta com a"
                    + " exportação do client-service e do training.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Os dados do cliente."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não tem o papel de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ExportResponse export(@AuthenticationPrincipal Jwt jwt) {
        return ExportResponse.of(exportNutritionData.mine(jwt.getSubject()));
    }
}
