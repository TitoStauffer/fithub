package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageAssessments;
import com.fithub.client.domain.assessment.Assessment;
import com.fithub.client.domain.assessment.BodyFat;
import com.fithub.client.domain.assessment.BodySeries;
import com.fithub.client.domain.assessment.Circumferences;
import com.fithub.client.domain.assessment.InvalidAssessmentException;
import com.fithub.client.domain.assessment.Skinfolds;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avaliações corporais (DEV-0044). O tipo vem da rota: o cliente registra as online em {@code /clients/me}; o
 * profissional vinculado, as presenciais em {@code /professionals/me/clients/{clientId}}.
 */
@RestController
@Tag(name = "Avaliações", description = "Avaliações corporais do cliente")
class AssessmentController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageAssessments manageAssessments;

    AssessmentController(ManageAssessments manageAssessments) {
        this.manageAssessments = manageAssessments;
    }

    @Schema(name = "Circumferences", description = "Circunferências em cm, todas opcionais.")
    record CircumferencesBody(
            @Schema(nullable = true) BigDecimal neck,
            @Schema(nullable = true) BigDecimal chest,
            @Schema(nullable = true) BigDecimal waist,
            @Schema(nullable = true) BigDecimal abdomen,
            @Schema(nullable = true) BigDecimal hip,
            @Schema(nullable = true) BigDecimal arm,
            @Schema(nullable = true) BigDecimal forearm,
            @Schema(nullable = true) BigDecimal thigh,
            @Schema(nullable = true) BigDecimal calf) {

        Circumferences toDomain() {
            return new Circumferences(neck, chest, waist, abdomen, hip, arm, forearm, thigh, calf);
        }

        static CircumferencesBody of(Circumferences c) {
            return new CircumferencesBody(
                    c.neck(), c.chest(), c.waist(), c.abdomen(), c.hip(), c.arm(), c.forearm(), c.thigh(), c.calf());
        }
    }

    @Schema(name = "Skinfolds", description = "Dobras cutâneas em mm, só na avaliação presencial.")
    record SkinfoldsBody(
            @Schema(nullable = true) BigDecimal chest,
            @Schema(nullable = true) BigDecimal midaxillary,
            @Schema(nullable = true) BigDecimal triceps,
            @Schema(nullable = true) BigDecimal subscapular,
            @Schema(nullable = true) BigDecimal abdomen,
            @Schema(nullable = true) BigDecimal suprailiac,
            @Schema(nullable = true) BigDecimal thigh) {

        Skinfolds toDomain() {
            return new Skinfolds(chest, midaxillary, triceps, subscapular, abdomen, suprailiac, thigh);
        }

        static SkinfoldsBody of(Skinfolds s) {
            return new SkinfoldsBody(
                    s.chest(), s.midaxillary(), s.triceps(), s.subscapular(), s.abdomen(), s.suprailiac(), s.thigh());
        }
    }

    /** Origem de cada % de gordura (FR-19): os métodos não são comparáveis entre si. */
    enum BodyFatOrigin {
        POLLOCK_3,
        POLLOCK_7,
        NAVY,
        BIOIMPEDANCE
    }

    @Schema(name = "BodyFatEstimate")
    record BodyFatEstimate(BodyFatOrigin origin, BigDecimal percent) {

        static List<BodyFatEstimate> of(BodyFat bodyFat) {
            List<BodyFatEstimate> estimates = new ArrayList<>();
            if (bodyFat.pollock3() != null) {
                estimates.add(new BodyFatEstimate(BodyFatOrigin.POLLOCK_3, bodyFat.pollock3()));
            }
            if (bodyFat.pollock7() != null) {
                estimates.add(new BodyFatEstimate(BodyFatOrigin.POLLOCK_7, bodyFat.pollock7()));
            }
            if (bodyFat.navy() != null) {
                estimates.add(new BodyFatEstimate(BodyFatOrigin.NAVY, bodyFat.navy()));
            }
            if (bodyFat.bioimpedance() != null) {
                estimates.add(new BodyFatEstimate(BodyFatOrigin.BIOIMPEDANCE, bodyFat.bioimpedance()));
            }
            return estimates;
        }
    }

    @Schema(name = "AssessmentRequest")
    record AssessmentRequest(
            LocalDate measuredOn,

            @Schema(description = "Peso em kg, de 20 a 400.")
            BigDecimal weightKg,

            @Schema(nullable = true) CircumferencesBody circumferences,

            @Schema(nullable = true, description = "Sexo usado só nos cálculos de % de gordura.")
            Assessment.Sex sexForFormulas,

            @Schema(nullable = true) SkinfoldsBody skinfolds,

            @Schema(nullable = true, description = "% de gordura de uma bioimpedância, de 3 a 70.")
            BigDecimal bioimpedanceBodyFatPercent) {

        ManageAssessments.Command toCommand() {
            return new ManageAssessments.Command(
                    measuredOn,
                    weightKg,
                    circumferences == null ? null : circumferences.toDomain(),
                    sexForFormulas,
                    skinfolds == null ? null : skinfolds.toDomain(),
                    bioimpedanceBodyFatPercent);
        }
    }

    @Schema(name = "Assessment")
    record AssessmentResponse(
            UUID id,
            Assessment.Type type,
            LocalDate measuredOn,
            BigDecimal weightKg,
            CircumferencesBody circumferences,
            @Schema(nullable = true) Assessment.Sex sexForFormulas,
            SkinfoldsBody skinfolds,

            @Schema(description = "% de gordura de cada origem; vazio quando não há dados para calcular.")
            List<BodyFatEstimate> bodyFat,

            UUID authorId,
            Assessment.AuthorRole authorRole,
            Instant createdAt) {

        static AssessmentResponse of(Assessment a) {
            return new AssessmentResponse(
                    a.id(),
                    a.type(),
                    a.measuredOn(),
                    a.weightKg(),
                    CircumferencesBody.of(a.circumferences()),
                    a.sexForFormulas(),
                    SkinfoldsBody.of(a.skinfolds()),
                    BodyFatEstimate.of(a.bodyFat()),
                    a.authorId(),
                    a.authorRole(),
                    a.createdAt());
        }
    }

    @PostMapping(path = "/clients/me/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Registra uma avaliação online (autoaferida) do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "201", description = "Avaliação registrada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo inválido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<AssessmentResponse> recordOnline(
            @AuthenticationPrincipal Jwt jwt, @RequestBody AssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssessmentResponse.of(manageAssessments.recordOnline(jwt.getSubject(), request.toCommand())));
    }

    @GetMapping(path = "/clients/me/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Histórico de avaliações do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Da medição mais recente para a mais antiga."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    List<AssessmentResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return manageAssessments.listMine(jwt.getSubject()).stream()
                .map(AssessmentResponse::of)
                .toList();
    }

    @DeleteMapping("/clients/me/assessments/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exclui uma avaliação do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "204", description = "Avaliação excluída.", content = @Content()),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Avaliação não encontrada (ou de outra pessoa).",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        manageAssessments.deleteMine(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/professionals/me/clients/{clientId}/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
    @Operation(
            summary = "Registra uma avaliação presencial de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "201", description = "Avaliação registrada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo inválido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<AssessmentResponse> recordPresential(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @RequestBody AssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssessmentResponse.of(
                        manageAssessments.recordPresential(jwt.getSubject(), clientId, request.toCommand())));
    }

    @GetMapping(path = "/professionals/me/clients/{clientId}/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
    @Operation(
            summary = "Histórico de avaliações de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Da medição mais recente para a mais antiga."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    List<AssessmentResponse> forClient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return manageAssessments.listForClient(jwt.getSubject(), clientId).stream()
                .map(AssessmentResponse::of)
                .toList();
    }

    @Schema(name = "BodySeriesPoint", description = "Um valor numa data de medição.")
    record PointResponse(LocalDate date, BigDecimal value) {}

    @Schema(
            name = "BodyFatSeries",
            description = "A série do % de gordura de um método; métodos não se misturam (BR-12).")
    record FatSeriesResponse(BodySeries.FatMethod origin, List<PointResponse> points) {}

    @Schema(name = "BodySeries", description = "A série histórica de peso, circunferências e % de gordura por método.")
    record SeriesResponse(
            List<PointResponse> weightKg,

            @Schema(
                    description = "Uma série por circunferência medida (neck, chest, waist, abdomen, hip, arm, forearm,"
                            + " thigh, calf), em cm.")
            Map<String, List<PointResponse>> circumferences,

            @Schema(description = "Uma série por método de % de gordura, sem misturar métodos.")
            List<FatSeriesResponse> bodyFat) {

        static SeriesResponse of(BodySeries series) {
            Map<String, List<PointResponse>> circumferences = new LinkedHashMap<>();
            series.circumferences().forEach((name, points) -> circumferences.put(name, points(points)));
            return new SeriesResponse(
                    points(series.weightKg()),
                    circumferences,
                    series.bodyFat().entrySet().stream()
                            .map(entry -> new FatSeriesResponse(entry.getKey(), points(entry.getValue())))
                            .toList());
        }

        private static List<PointResponse> points(List<BodySeries.Point> points) {
            return points.stream()
                    .map(point -> new PointResponse(point.date(), point.value()))
                    .toList();
        }
    }

    @GetMapping(path = "/clients/me/assessments/series", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            operationId = "getMyBodySeries",
            summary = "A série histórica da evolução corporal do cliente (todo o histórico, ou de from a to)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Peso, circunferências e % de gordura por método."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Período invertido; o campo vem em errors.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    SeriesResponse mySeries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return SeriesResponse.of(manageAssessments.seriesMine(jwt.getSubject(), from, to));
    }

    @GetMapping(
            path = "/professionals/me/clients/{clientId}/assessments/series",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
    @Operation(
            operationId = "getClientBodySeries",
            summary = "A série histórica da evolução corporal de um cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Peso, circunferências e % de gordura por método."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Período invertido; o campo vem em errors.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    SeriesResponse clientSeries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return SeriesResponse.of(manageAssessments.seriesForClient(jwt.getSubject(), clientId, from, to));
    }

    @ExceptionHandler(InvalidAssessmentException.class)
    ProblemDetail invalid(InvalidAssessmentException exception) {
        ProblemDetail problem = InvitationController.problem(
                HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(ManageAssessments.NotFoundException.class)
    ProblemDetail notFound(ManageAssessments.NotFoundException exception) {
        return InvitationController.problem(HttpStatus.NOT_FOUND, "Não encontrado", exception.getMessage());
    }
}
