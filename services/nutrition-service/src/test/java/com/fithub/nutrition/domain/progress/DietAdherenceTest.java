package com.fithub.nutrition.domain.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O período das consultas e o cálculo da aderência à dieta (DEV-0092). */
class DietAdherenceTest {

    private static final LocalDate D0 = LocalDate.of(2026, 11, 2);
    private static final UUID PLAN = UUID.randomUUID();

    private static DietAdherence.PlanWindow plan(LocalDate to, DietAdherence.Version... versions) {
        return new DietAdherence.PlanWindow(PLAN, to, List.of(versions));
    }

    private static DietAdherence.Version version(LocalDate publishedOn, int meals) {
        return new DietAdherence.Version(publishedOn, meals);
    }

    private static DietAdherence.Log log(LocalDate day, String status) {
        return new DietAdherence.Log(day, PLAN, status);
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
        assertThat(standard.from()).isEqualTo(today.minusDays(27));
        assertThat(standard.to()).isEqualTo(today);
        assertThat(ProgressPeriod.of(today.minusDays(181), today, today).from()).isEqualTo(today.minusDays(181));
        assertField(() -> ProgressPeriod.of(today.minusDays(182), today, today), "from");
        assertField(() -> ProgressPeriod.of(today.plusDays(1), today, today), "from");
    }

    @Test
    void feitaValeUmParcialMeioENaoFeitaZero() {
        // 4 refeições por dia, em 2 dias com 1 feita e 1 parcial por dia: 3 de 8 (37,5%).
        DietAdherence.Result result = DietAdherence.of(
                new ProgressPeriod(D0, D0.plusDays(1)),
                List.of(plan(null, version(D0.minusDays(10), 4))),
                List.of(
                        log(D0, "DONE"),
                        log(D0, "PARTIAL"),
                        log(D0, "SKIPPED"),
                        log(D0.plusDays(1), "DONE"),
                        log(D0.plusDays(1), "PARTIAL")));
        assertThat(result.done()).isEqualByComparingTo("3.0");
        assertThat(result.prescribed()).isEqualTo(8);
        assertThat(result.percent()).isEqualByComparingTo("37.5");
        assertThat(result.days()).hasSize(2);
        assertThat(result.days().get(0).day()).isEqualTo(D0);
        assertThat(result.days().get(0).done()).isEqualByComparingTo("1.5");
        assertThat(result.days().get(0).prescribed()).isEqualTo(4);
        assertThat(DietAdherence.valueOf("SKIPPED")).isEqualByComparingTo("0");
    }

    @Test
    void prescritasSaoAsDaVersaoEmVigorEnquantoOPlanoValia() {
        ProgressPeriod threeDays = new ProgressPeriod(D0, D0.plusDays(2));
        // A versão 2, com 2 refeições, vale do terceiro dia em diante.
        DietAdherence.Result edited = DietAdherence.of(
                threeDays, List.of(plan(null, version(D0.minusDays(5), 4), version(D0.plusDays(2), 2))), List.of());
        assertThat(edited.days()).extracting(DietAdherence.Day::prescribed).containsExactly(4, 4, 2);

        // Substituído no segundo dia: no terceiro não prescreve, e o registro dele não conta.
        DietAdherence.Result replaced = DietAdherence.of(
                threeDays,
                List.of(plan(D0.plusDays(1), version(D0.minusDays(5), 4))),
                List.of(log(D0.plusDays(2), "DONE")));
        assertThat(replaced.days()).extracting(DietAdherence.Day::prescribed).containsExactly(4, 4, 0);
        assertThat(replaced.done()).isEqualByComparingTo("0.0");

        // Publicado no terceiro dia: antes dele, nada prescrito nem contado.
        DietAdherence.Result late = DietAdherence.of(
                threeDays,
                List.of(plan(null, version(D0.plusDays(2), 3))),
                List.of(log(D0, "DONE"), log(D0.plusDays(2), "DONE")));
        assertThat(late.prescribed()).isEqualTo(3);
        assertThat(late.done()).isEqualByComparingTo("1.0");
    }

    @Test
    void semPlanoNaoHaPercentualERegistroDeOutroPlanoNaoConta() {
        DietAdherence.Result empty = DietAdherence.of(new ProgressPeriod(D0, D0), List.of(), List.of(log(D0, "DONE")));
        assertThat(empty.prescribed()).isZero();
        assertThat(empty.percent()).isNull();
        DietAdherence.Result other = DietAdherence.of(
                new ProgressPeriod(D0, D0),
                List.of(plan(null, version(D0, 2))),
                List.of(new DietAdherence.Log(D0, UUID.randomUUID(), "DONE")));
        assertThat(other.done()).isEqualByComparingTo("0.0");
        assertThat(other.percent()).isEqualByComparingTo("0.0");
    }
}
