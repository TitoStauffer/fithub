package com.fithub.training.domain.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O período das consultas e o cálculo da aderência ao treino (DEV-0091). */
class TrainingAdherenceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private static TrainingAdherence.PlanWindow plan(LocalDate from, LocalDate to, int divisions) {
        return new TrainingAdherence.PlanWindow(UUID.randomUUID(), from, to, divisions);
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOf(InvalidProgressException.class)
                .extracting(error -> ((InvalidProgressException) error).field())
                .isEqualTo(field);
    }

    @Test
    void periodoPadraoSaoAsUltimas4SemanasEVaiAte26() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        ProgressPeriod standard = ProgressPeriod.of(null, null, today);
        assertThat(standard.to()).isEqualTo(today);
        assertThat(standard.from()).isEqualTo(today.minusDays(27));
        assertThat(standard.days()).isEqualTo(28);
        assertThat(ProgressPeriod.of(null, today.minusDays(1), today).from()).isEqualTo(today.minusDays(28));
        assertThat(ProgressPeriod.of(today, today, today).days()).isEqualTo(1);
        assertThat(ProgressPeriod.of(today.minusDays(181), today, today).days()).isEqualTo(182);
        assertField(() -> ProgressPeriod.of(today.minusDays(182), today, today), "from");
        assertField(() -> ProgressPeriod.of(today.plusDays(1), today, today), "from");
    }

    @Test
    void fichaAbcEsperaTresPorSemanaEContaOsFinalizados() {
        ProgressPeriod twoWeeks = new ProgressPeriod(MONDAY, MONDAY.plusDays(13));
        TrainingAdherence.Result result = TrainingAdherence.of(
                twoWeeks,
                List.of(plan(MONDAY.minusDays(30), null, 3)),
                List.of(MONDAY.plusDays(1), MONDAY.plusDays(3), MONDAY.plusDays(8), MONDAY.minusDays(1)));
        assertThat(result.weeks()).hasSize(2);
        assertThat(result.weeks().get(0).start()).isEqualTo(MONDAY);
        assertThat(result.weeks().get(0).end()).isEqualTo(MONDAY.plusDays(6));
        assertThat(result.weeks().get(0).done()).isEqualTo(2);
        assertThat(result.weeks().get(0).expected()).isEqualByComparingTo("3.0");
        assertThat(result.weeks().get(1).done()).isEqualTo(1);
        assertThat(result.done()).isEqualTo(3);
        assertThat(result.expected()).isEqualByComparingTo("6.0");
        assertThat(result.percent()).isEqualByComparingTo("50.0");
    }

    @Test
    void esperadoProporcionalAosDiasEmQueCadaFichaValeu() {
        ProgressPeriod week = new ProgressPeriod(MONDAY, MONDAY.plusDays(6));
        // Publicada na quinta: 4 dias da semana, 3 × 4 / 7 = 1,7.
        assertThat(TrainingAdherence.of(week, List.of(plan(MONDAY.plusDays(3), null, 3)), List.of())
                        .expected())
                .isEqualByComparingTo("1.7");
        // A ABC vale até quarta (3 dias, 1,29) e a AB nova de quinta em diante (4 dias, 1,14): 2,4.
        TrainingAdherence.Result swapped = TrainingAdherence.of(
                week,
                List.of(plan(MONDAY.minusDays(10), MONDAY.plusDays(2), 3), plan(MONDAY.plusDays(3), null, 2)),
                List.of(MONDAY));
        assertThat(swapped.expected()).isEqualByComparingTo("2.4");
        // O percentual sai do esperado exato (17/7), não do arredondado: 1 / 2,43 = 41,2%.
        assertThat(swapped.percent()).isEqualByComparingTo("41.2");
        // A ficha que acabou antes do período não espera nada nele.
        assertThat(TrainingAdherence.of(week, List.of(plan(MONDAY.minusDays(30), MONDAY.minusDays(10), 3)), List.of())
                        .expected())
                .isEqualByComparingTo("0.0");
        // A que começa depois também não.
        assertThat(TrainingAdherence.of(week, List.of(plan(MONDAY.plusDays(20), null, 3)), List.of())
                        .expected())
                .isEqualByComparingTo("0.0");
    }

    @Test
    void ultimaSemanaPodeSerMenorESemFichaNaoHaPercentual() {
        ProgressPeriod tenDays = new ProgressPeriod(MONDAY, MONDAY.plusDays(9));
        TrainingAdherence.Result result =
                TrainingAdherence.of(tenDays, List.of(plan(MONDAY, null, 3)), List.of(MONDAY.plusDays(9)));
        assertThat(result.weeks()).hasSize(2);
        assertThat(result.weeks().get(1).start()).isEqualTo(MONDAY.plusDays(7));
        assertThat(result.weeks().get(1).end()).isEqualTo(MONDAY.plusDays(9));
        assertThat(result.weeks().get(1).expected()).isEqualByComparingTo("1.3");
        assertThat(result.weeks().get(1).done()).isEqualTo(1);

        TrainingAdherence.Result empty = TrainingAdherence.of(tenDays, List.of(), List.of(MONDAY));
        assertThat(empty.expected()).isEqualByComparingTo("0.0");
        assertThat(empty.percent()).isNull();
        assertThat(empty.done()).isEqualTo(1);
    }
}
