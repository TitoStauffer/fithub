package com.fithub.nutrition.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.nutrition.application.DietProgress;
import com.fithub.nutrition.domain.progress.DietAdherence;
import com.fithub.nutrition.domain.progress.InvalidProgressException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** As respostas da evolução da dieta (DEV-0092), iguais nas rotas da nutricionista e do paciente. */
final class DietProgressResponses {

    private DietProgressResponses() {}

    @Schema(name = "DietAdherenceDay", description = "Um dia do período: o feito e as refeições prescritas.")
    record DayResponse(
            LocalDate day,

            @Schema(description = "Feita vale 1, parcial 0,5.")
            BigDecimal done,

            int prescribed) {}

    @Schema(name = "DietAdherence", description = "Refeições feitas contra prescritas no período.")
    record AdherenceResponse(
            BigDecimal done,
            int prescribed,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Ausente sem refeição prescrita.")
            BigDecimal percent,

            List<DayResponse> days) {}

    @Schema(name = "AlternativeUse", description = "Uma alternativa escolhida no período e quantas vezes.")
    record AlternativeResponse(UUID foodId, String foodName, long times) {}

    @Schema(name = "DietDeviation", description = "Um registro do período com desvio ou foto do prato.")
    record DeviationResponse(
            UUID logId,
            LocalDate day,
            int mealPosition,
            String mealName,

            @JsonInclude(JsonInclude.Include.NON_NULL) String deviation,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "URL temporária (5 minutos) para ver a foto do prato.")
            URI photoUrl,

            @JsonInclude(JsonInclude.Include.NON_NULL) Instant photoUrlExpiresAt) {}

    @Schema(name = "DietProgress", description = "A evolução da dieta no período (FR-57, FR-58).")
    record ProgressResponse(
            LocalDate from,
            LocalDate to,
            AdherenceResponse adherence,

            @Schema(description = "As 10 alternativas mais escolhidas.")
            List<AlternativeResponse> alternatives,

            @Schema(description = "Do mais novo para o mais antigo.")
            List<DeviationResponse> deviations) {

        static ProgressResponse of(DietProgress.View view) {
            DietAdherence.Result adherence = view.adherence();
            return new ProgressResponse(
                    view.period().from(),
                    view.period().to(),
                    new AdherenceResponse(
                            adherence.done(),
                            adherence.prescribed(),
                            adherence.percent(),
                            adherence.days().stream()
                                    .map(day -> new DayResponse(day.day(), day.done(), day.prescribed()))
                                    .toList()),
                    view.alternatives().stream()
                            .map(use -> new AlternativeResponse(use.foodId(), use.foodName(), use.times()))
                            .toList(),
                    view.deviations().stream()
                            .map(each -> new DeviationResponse(
                                    each.deviation().logId(),
                                    each.deviation().day(),
                                    each.deviation().mealPosition(),
                                    each.deviation().mealName(),
                                    each.deviation().deviation(),
                                    each.photoUrl(),
                                    each.photoUrlExpiresAt()))
                            .toList());
        }
    }

    static ProblemDetail invalid(InvalidProgressException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }
}
