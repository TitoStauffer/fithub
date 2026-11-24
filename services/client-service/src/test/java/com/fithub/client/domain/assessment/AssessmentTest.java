package com.fithub.client.domain.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AssessmentTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-23");
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final UUID CLIENT = UUID.randomUUID();

    private static Assessment online(LocalDate measuredOn, String weight, Circumferences circumferences) {
        return Assessment.record(
                UUID.randomUUID(),
                CLIENT,
                Assessment.Type.ONLINE,
                measuredOn,
                weight == null ? null : new BigDecimal(weight),
                circumferences,
                null,
                null,
                CLIENT,
                Assessment.AuthorRole.CLIENT,
                null,
                null,
                null,
                TODAY,
                NOW);
    }

    private static String fieldOf(Runnable action) {
        try {
            action.run();
        } catch (InvalidAssessmentException invalid) {
            return invalid.field();
        }
        throw new AssertionError("a avaliação deveria ter sido recusada");
    }

    @Test
    void avaliacaoValidaSemCircunferenciasFicaComTodasVazias() {
        Assessment assessment = online(TODAY, "72.5", null);

        assertThat(assessment.weightKg()).isEqualByComparingTo("72.5");
        assertThat(assessment.circumferences()).isEqualTo(Circumferences.NONE);
    }

    @Test
    void medicaoNoFuturoERecusadaEHojeEAceita() {
        assertThat(fieldOf(() -> online(TODAY.plusDays(1), "72", null))).isEqualTo("measuredOn");
        assertThat(online(TODAY, "72", null).measuredOn()).isEqualTo(TODAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"19.9", "400.1", "72.55"})
    void pesoForaDaFaixaOuComMaisDeUmaCasaERecusado(String weight) {
        assertThat(fieldOf(() -> online(TODAY, weight, null))).isEqualTo("weightKg");
    }

    @Test
    void limitesDoPesoSaoAceitos() {
        assertThat(online(TODAY, "20", null).weightKg()).isEqualByComparingTo("20");
        assertThat(online(TODAY, "400.0", null).weightKg()).isEqualByComparingTo("400");
    }

    @Test
    void circunferenciaForaDaFaixaERecusadaComOCampo() {
        assertThat(fieldOf(() ->
                        new Circumferences(null, null, new BigDecimal("9.9"), null, null, null, null, null, null)))
                .isEqualTo("circumferences.waist");
        assertThat(fieldOf(() ->
                        new Circumferences(new BigDecimal("300.1"), null, null, null, null, null, null, null, null)))
                .isEqualTo("circumferences.neck");
    }

    @Test
    void presencialSoDoProfissionalEOnlineSoDoCliente() {
        assertThatThrownBy(() -> new Assessment(
                        UUID.randomUUID(),
                        CLIENT,
                        Assessment.Type.PRESENTIAL,
                        TODAY,
                        new BigDecimal("70"),
                        null,
                        null,
                        null,
                        null,
                        CLIENT,
                        Assessment.AuthorRole.CLIENT,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Dobra no índice informado (peitoral, axilar média, tríceps, subescapular, abdominal, suprailíaca, coxa). */
    private static Skinfolds onlyFold(int index, String mm) {
        BigDecimal[] values = new BigDecimal[7];
        values[index] = new BigDecimal(mm);
        return new Skinfolds(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }

    private static final String[] FOLD_FIELDS = {
        "skinfolds.chest",
        "skinfolds.midaxillary",
        "skinfolds.triceps",
        "skinfolds.subscapular",
        "skinfolds.abdomen",
        "skinfolds.suprailiac",
        "skinfolds.thigh"
    };

    @ParameterizedTest
    @ValueSource(strings = {"0.9", "80.1", "10.55"})
    void cadaDobraForaDaFaixaOuComMaisDeUmaCasaERecusadaComOCampo(String mm) {
        for (int index = 0; index < FOLD_FIELDS.length; index++) {
            int fold = index;
            assertThat(fieldOf(() -> onlyFold(fold, mm))).as(FOLD_FIELDS[index]).isEqualTo(FOLD_FIELDS[index]);
        }
    }

    @Test
    void limitesDasDobrasSaoAceitos() {
        assertThat(onlyFold(2, "1").triceps()).isEqualByComparingTo("1");
        assertThat(onlyFold(6, "80.0").thigh()).isEqualByComparingTo("80");
    }
}
