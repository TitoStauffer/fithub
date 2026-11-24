package com.fithub.training.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cálculo dos recordes e regras do lançamento manual (DEV-0068). */
class PersonalRecordsTest {

    private static final UUID SQUAT = UUID.randomUUID();
    private static final UUID BENCH = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private static PersonalRecords.Performance mark(UUID exercise, String load, int reps, LocalDate date) {
        return new PersonalRecords.Performance(
                exercise, new BigDecimal(load), reps, date, PersonalRecords.Source.SET, UUID.randomUUID());
    }

    @Test
    void epleyComUmaRepeticaoEAPropriaCargaEAcimaDe12NaoEstima() {
        assertThat(PersonalRecords.estimatedOneRepMax(new BigDecimal("100"), 1))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("100"));
        // 90 × (1 + 5/30) = 105.
        assertThat(PersonalRecords.estimatedOneRepMax(new BigDecimal("90"), 5))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("105"));
        // 60 × (1 + 12/30) = 84.
        assertThat(PersonalRecords.estimatedOneRepMax(new BigDecimal("60"), 12))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("84"));
        assertThat(PersonalRecords.estimatedOneRepMax(new BigDecimal("60"), 13)).isEmpty();
        assertThat(PersonalRecords.estimatedOneRepMax(new BigDecimal("60"), 0)).isEmpty();
    }

    @Test
    void maiorCargaE1rmPorExercicioComEmpateNaMarcaMaisAntiga() {
        PersonalRecords.Performance first100 = mark(SQUAT, "100", 1, TODAY.minusDays(10));
        PersonalRecords.Performance later100 = mark(SQUAT, "100", 1, TODAY.minusDays(2));
        PersonalRecords.Performance ninetyByFive = mark(SQUAT, "90", 5, TODAY.minusDays(5));
        PersonalRecords.Performance fifteenReps = mark(BENCH, "50", 15, TODAY.minusDays(1));
        PersonalRecords.Performance noReps = mark(BENCH, "200", 0, TODAY.minusDays(1));

        List<PersonalRecords.ExerciseRecords> records =
                PersonalRecords.of(List.of(first100, ninetyByFive, later100, fifteenReps, noReps));

        assertThat(records).hasSize(2);
        PersonalRecords.ExerciseRecords squat = records.get(0);
        assertThat(squat.exerciseId()).isEqualTo(SQUAT);
        assertThat(squat.maxLoad().valueKg()).isEqualByComparingTo("100");
        assertThat(squat.maxLoad().performance()).isEqualTo(first100);
        assertThat(squat.estimatedOneRepMax()).hasValueSatisfying(mark -> {
            assertThat(mark.valueKg()).isEqualByComparingTo("105");
            assertThat(mark.performance()).isEqualTo(ninetyByFive);
        });

        // Série sem repetição não conta; com 15, conta só para a maior carga.
        PersonalRecords.ExerciseRecords bench = records.get(1);
        assertThat(bench.maxLoad().valueKg()).isEqualByComparingTo("50");
        assertThat(bench.estimatedOneRepMax()).isEmpty();
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidRecordException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    private static ManualRecord manual(String load, Integer reps, LocalDate date) {
        return ManualRecord.of(
                UUID.randomUUID(), SQUAT, load == null ? null : new BigDecimal(load), reps, date, TODAY, NOW);
    }

    @Test
    void lancamentoManualValidaCargaRepeticoesEData() {
        ManualRecord record = manual("102.5", 1, TODAY);
        assertThat(record.loadKg()).isEqualByComparingTo("102.5");
        assertThat(record.performance().source()).isEqualTo(PersonalRecords.Source.MANUAL);
        assertThat(record.performance().sourceId()).isEqualTo(record.id());
        assertThat(manual("1000", 300, TODAY.minusYears(1)).reps()).isEqualTo(300);

        assertInvalid("loadKg", () -> manual(null, 1, TODAY));
        assertInvalid("loadKg", () -> manual("0", 1, TODAY));
        assertInvalid("loadKg", () -> manual("1000.25", 1, TODAY));
        assertInvalid("loadKg", () -> manual("10.1", 1, TODAY));
        assertInvalid("reps", () -> manual("10", null, TODAY));
        assertInvalid("reps", () -> manual("10", 0, TODAY));
        assertInvalid("reps", () -> manual("10", 301, TODAY));
        assertInvalid("performedOn", () -> manual("10", 1, null));
        assertInvalid("performedOn", () -> manual("10", 1, TODAY.plusDays(1)));
        assertInvalid(
                "exerciseId", () -> ManualRecord.of(UUID.randomUUID(), null, BigDecimal.TEN, 1, TODAY, TODAY, NOW));
    }
}
