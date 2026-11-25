package com.fithub.client.domain.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A série da evolução corporal (DEV-0093): ordem, período e um método de % de gordura por série (BR-12). */
class BodySeriesTest {

    private static final LocalDate D0 = LocalDate.of(2026, 10, 1);

    private static Assessment assessment(
            LocalDate day, String weight, String waist, BodyFat bodyFat, Instant createdAt) {
        return new Assessment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Assessment.Type.ONLINE,
                day,
                weight == null ? null : new BigDecimal(weight),
                new Circumferences(
                        null, null, waist == null ? null : new BigDecimal(waist), null, null, null, null, null, null),
                null,
                null,
                bodyFat,
                UUID.randomUUID(),
                Assessment.AuthorRole.CLIENT,
                createdAt);
    }

    private static BodyFat fat(String pollock3, String navy, String bia) {
        return new BodyFat(
                pollock3 == null ? null : new BigDecimal(pollock3),
                null,
                navy == null ? null : new BigDecimal(navy),
                navy == null ? null : 165,
                bia == null ? null : new BigDecimal(bia));
    }

    @Test
    void ordemPorDataEUmaSeriePorMetodoSemMisturar() {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        List<Assessment> assessments = List.of(
                assessment(D0.plusDays(20), "70.0", "71", fat(null, "25.0", "24.0"), now),
                assessment(D0, "72.5", null, fat("22.0", null, null), now),
                assessment(D0.plusDays(10), "71.0", "72", BodyFat.NONE, now));
        BodySeries series = BodySeries.of(assessments, null, null);

        assertThat(series.weightKg())
                .extracting(BodySeries.Point::date)
                .containsExactly(D0, D0.plusDays(10), D0.plusDays(20));
        assertThat(series.weightKg().get(0).value()).isEqualByComparingTo("72.5");
        assertThat(series.circumferences()).containsOnlyKeys("waist");
        assertThat(series.circumferences().get("waist"))
                .extracting(BodySeries.Point::date)
                .containsExactly(D0.plusDays(10), D0.plusDays(20));
        assertThat(series.bodyFat())
                .containsOnlyKeys(
                        BodySeries.FatMethod.POLLOCK_3, BodySeries.FatMethod.NAVY, BodySeries.FatMethod.BIOIMPEDANCE);
        assertThat(series.bodyFat().get(BodySeries.FatMethod.NAVY)).hasSize(1);
        assertThat(series.bodyFat().get(BodySeries.FatMethod.NAVY).get(0).value())
                .isEqualByComparingTo("25.0");
        assertThat(series.bodyFat()
                        .get(BodySeries.FatMethod.BIOIMPEDANCE)
                        .get(0)
                        .value())
                .isEqualByComparingTo("24.0");
        assertThat(series.bodyFat().get(BodySeries.FatMethod.POLLOCK_3).get(0).date())
                .isEqualTo(D0);
    }

    @Test
    void periodoIncluiAsPontasEInvertidoResponde400() {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        List<Assessment> assessments = List.of(
                assessment(D0, "72.5", null, BodyFat.NONE, now),
                assessment(D0.plusDays(10), "71.0", null, BodyFat.NONE, now),
                assessment(D0.plusDays(20), "70.0", null, BodyFat.NONE, now));
        assertThat(BodySeries.of(assessments, D0.plusDays(10), D0.plusDays(20)).weightKg())
                .extracting(BodySeries.Point::date)
                .containsExactly(D0.plusDays(10), D0.plusDays(20));
        assertThat(BodySeries.of(assessments, D0, D0.plusDays(10)).weightKg()).hasSize(2);
        assertThatThrownBy(() -> BodySeries.of(assessments, D0.plusDays(1), D0))
                .isInstanceOf(InvalidAssessmentException.class)
                .extracting(error -> ((InvalidAssessmentException) error).field())
                .isEqualTo("from");
    }

    @Test
    void mesmaDataVaiPelaOrdemEmQueFoiRegistrada() {
        List<Assessment> assessments = List.of(
                assessment(D0, "71.0", null, BodyFat.NONE, Instant.parse("2026-11-25T13:00:00Z")),
                assessment(D0, "72.0", null, BodyFat.NONE, Instant.parse("2026-11-25T12:00:00Z")));
        assertThat(BodySeries.of(assessments, null, null).weightKg())
                .extracting(point -> point.value().toPlainString())
                .containsExactly("72.0", "71.0");
    }
}
