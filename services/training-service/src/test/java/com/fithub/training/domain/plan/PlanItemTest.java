package com.fithub.training.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do exercício prescrito (DEV-0060): séries, repetições fixas ou em faixa, carga, descanso e alternativas. */
class PlanItemTest {

    private static final UUID SQUAT = UUID.randomUUID();
    private static final UUID LEG_PRESS = UUID.randomUUID();

    private static PlanItem.Draft item(Integer sets, Integer repsMin, Integer repsMax) {
        return new PlanItem.Draft(SQUAT, sets, repsMin, repsMax, null, null, null, null, null);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidPlanException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void repeticoesFixasOuEmFaixa() {
        PlanItem fixed = item(3, 10, null).toItem();
        assertThat(fixed.repsMin()).isEqualTo(10);
        assertThat(fixed.repsMax()).isEqualTo(10);
        assertThat(fixed.repsRange()).isFalse();

        PlanItem range = item(4, 8, 12).toItem();
        assertThat(range.repsRange()).isTrue();

        assertInvalid("repsMax", () -> item(3, 12, 8).toItem());
        assertInvalid("repsMax", () -> item(3, 8, 101).toItem());
        assertInvalid("repsMin", () -> item(3, 0, null).toItem());
        assertInvalid("repsMin", () -> item(3, null, 12).toItem());
    }

    @Test
    void limitesDeSeriesCargaDescansoEObservacao() {
        assertInvalid("sets", () -> item(0, 10, null).toItem());
        assertInvalid("sets", () -> item(21, 10, null).toItem());
        assertInvalid("sets", () -> item(null, 10, null).toItem());
        assertInvalid("exerciseId", () -> new PlanItem.Draft(null, 3, 10, null, null, null, null, null, null).toItem());

        PlanItem loaded = new PlanItem.Draft(
                        SQUAT, 3, 10, null, new BigDecimal("42.5"), 90, "  Desça devagar.  ", "DROP_SET", null)
                .toItem();
        assertThat(loaded.loadKg()).isEqualByComparingTo("42.5");
        assertThat(loaded.restSeconds()).isEqualTo(90);
        assertThat(loaded.note()).isEqualTo("Desça devagar.");
        assertThat(loaded.technique()).isEqualTo(PlanItem.Technique.DROP_SET);

        for (String load : List.of("-0.5", "1000.5", "42.3")) {
            assertInvalid(
                    "loadKg",
                    () -> new PlanItem.Draft(SQUAT, 3, 10, null, new BigDecimal(load), null, null, null, null)
                            .toItem());
        }
        assertInvalid(
                "restSeconds", () -> new PlanItem.Draft(SQUAT, 3, 10, null, null, 601, null, null, null).toItem());
        assertInvalid(
                "note", () -> new PlanItem.Draft(SQUAT, 3, 10, null, null, null, "a".repeat(501), null, null).toItem());
        assertInvalid(
                "technique", () -> new PlanItem.Draft(SQUAT, 3, 10, null, null, null, null, "SUPERSET", null).toItem());
    }

    @Test
    void alternativasSaoOutrosExerciciosSemRepetirAteTres() {
        PlanItem item = new PlanItem.Draft(SQUAT, 3, 10, null, null, null, null, null, List.of(LEG_PRESS)).toItem();
        assertThat(item.exerciseIds()).containsExactly(SQUAT, LEG_PRESS);

        assertInvalid(
                "alternatives[0]",
                () -> new PlanItem.Draft(SQUAT, 3, 10, null, null, null, null, null, List.of(SQUAT)).toItem());
        assertInvalid(
                "alternatives[1]",
                () -> new PlanItem.Draft(SQUAT, 3, 10, null, null, null, null, null, List.of(LEG_PRESS, LEG_PRESS))
                        .toItem());
        List<UUID> four = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertInvalid(
                "alternatives", () -> new PlanItem.Draft(SQUAT, 3, 10, null, null, null, null, null, four).toItem());
    }

    @Test
    void erroDoItemApontaADivisaoEAPosicao() {
        assertInvalid(
                "divisions[1].items[0].sets",
                () -> PlanVersion.divisions(List.of(
                        PlanVersion.DivisionDraft.named("A"),
                        new PlanVersion.DivisionDraft("B", List.of(item(0, 10, null))))));
        List<PlanItem.Draft> thirtyOne = java.util.Collections.nCopies(31, item(3, 10, null));
        assertInvalid(
                "divisions[0].items",
                () -> PlanVersion.divisions(List.of(new PlanVersion.DivisionDraft("A", thirtyOne))));
    }
}
