package com.fithub.client.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.client.application.ExportClientData;
import com.fithub.client.domain.account.PrivacyRecords;
import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.ProfessionalAccessAudit;
import com.fithub.client.domain.professional.ProfessionalType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação dos dados do cliente no client-service (DEV-0104, NFR-01, LGPD art. 18, II e V). A conta vem sempre do
 * token. As fotos vêm por URL temporária de 5 minutos; o arquivo nunca passa pelo serviço.
 */
@RestController
@Tag(name = "Conta do cliente", description = "Exclusão e exportação dos dados do cliente autenticado")
class ClientExportController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ExportClientData exportClientData;

    ClientExportController(ExportClientData exportClientData) {
        this.exportClientData = exportClientData;
    }

    @Schema(name = "ClientExportAssessment", description = "Avaliação com as fotos, cada uma com a URL temporária.")
    record AssessmentExport(
            AssessmentController.AssessmentResponse assessment, List<PhotoController.PhotoResponse> photos) {}

    @Schema(name = "ClientExportConsent", description = "Consentimento dado no aceite do vínculo.")
    record ConsentExport(Set<SharedData> scope, String termsVersion, Instant grantedAt) {

        static ConsentExport of(PrivacyRecords.Consent consent) {
            return new ConsentExport(consent.scope(), consent.termsVersion(), consent.grantedAt());
        }
    }

    // Vínculo ativo não tem endedAt nem endedBy; sem consentimento, o campo também some (aprendizado
    // nulo-sobre-ref-no-openapi: campo que pode faltar é omitido, não null).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ClientExportLink", description = "Vínculo, ativo ou encerrado, com o consentimento.")
    record LinkExport(
            UUID id,
            UUID professionalId,
            String professionalName,
            ProfessionalType professionalType,
            Link.Status status,
            Instant startedAt,
            Instant endedAt,
            Link.Party endedBy,
            ConsentExport consent) {

        static LinkExport of(ExportClientData.LinkWithConsent found) {
            Link link = found.link();
            return new LinkExport(
                    link.id(),
                    link.professionalId(),
                    found.professionalName(),
                    link.type(),
                    link.status(),
                    link.startedAt(),
                    link.endedAt(),
                    link.endedBy(),
                    found.consent().map(ConsentExport::of).orElse(null));
        }
    }

    // O nome some quando o profissional saiu da projeção.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            name = "ClientExportHealthRead",
            description = "Leitura do perfil de saúde pelo profissional, sem o conteúdo.")
    record HealthReadExport(UUID professionalId, String professionalName, Instant accessedAt) {

        static HealthReadExport of(PrivacyRecords.HealthRead read) {
            return new HealthReadExport(read.professionalId(), read.professionalName(), read.accessedAt());
        }
    }

    // O nome some quando o profissional saiu da projeção.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ClientExportAccess", description = "Leitura de dado sensível pelo profissional, sem o conteúdo.")
    record AccessExport(
            UUID professionalId,
            String professionalName,
            ProfessionalAccessAudit.Resource resource,
            Instant accessedAt) {

        static AccessExport of(PrivacyRecords.AccessRecord access) {
            return new AccessExport(
                    access.professionalId(), access.professionalName(), access.resource(), access.accessedAt());
        }
    }

    // Sem perfil ou sem saúde, o campo some.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ClientExport", description = "Todos os dados que o client-service tem do cliente.")
    record ExportResponse(
            Instant exportedAt,

            @Schema(description = "Ausente se a conta ainda não tem perfil.")
            ClientProfileController.ProfileResponse profile,

            @Schema(description = "Ausente se o cliente nunca preencheu o perfil de saúde.")
            HealthProfileController.HealthResponse health,

            List<AssessmentExport> assessments,
            List<LinkExport> links,

            @Schema(description = "Só as leituras da saúde; o registro completo está em accessLog.")
            List<HealthReadExport> healthReads,

            @Schema(
                    description =
                            "Todas as leituras de dados sensíveis pelos profissionais (saúde, avaliações e fotos).")
            List<AccessExport> accessLog) {

        static ExportResponse of(ExportClientData.Export export) {
            return new ExportResponse(
                    export.exportedAt(),
                    export.profile()
                            .map(ClientProfileController.ProfileResponse::of)
                            .orElse(null),
                    export.health()
                            .map(HealthProfileController.HealthResponse::of)
                            .orElse(null),
                    export.assessments().stream()
                            .map(found -> new AssessmentExport(
                                    AssessmentController.AssessmentResponse.of(found.assessment()),
                                    found.photos().stream()
                                            .map(PhotoController.PhotoResponse::of)
                                            .toList()))
                            .toList(),
                    export.links().stream().map(LinkExport::of).toList(),
                    export.healthReads().stream().map(HealthReadExport::of).toList(),
                    export.accessLog().stream().map(AccessExport::of).toList());
        }
    }

    @GetMapping(path = "/clients/me/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exporta todos os dados que o client-service tem do cliente",
            description = "Perfil, saúde, avaliações com as fotos (URL temporária de 5 minutos), vínculos com o"
                    + " consentimento de cada um, as leituras da saúde (healthReads) e o registro completo das leituras"
                    + " de dados sensíveis pelos profissionais (accessLog), sem o conteúdo. O app junta com a exportação"
                    + " do training e do nutrition.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Os dados do cliente."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não tem o papel de cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ExportResponse export(@AuthenticationPrincipal Jwt jwt) {
        return ExportResponse.of(exportClientData.mine(jwt.getSubject()));
    }
}
