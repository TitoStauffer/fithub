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
}
