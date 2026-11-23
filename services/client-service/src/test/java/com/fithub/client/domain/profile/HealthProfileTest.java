package com.fithub.client.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HealthProfileTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static HealthProfile health(List<HealthProfile.Condition> conditions, String notes) {
        return HealthProfile.of("conta-1", conditions, notes, NOW, NOW);
    }

    @Test
    void condicoesRepetidasViramUmaSoNaOrdemDaDeclaracao() {
        HealthProfile profile = health(
                List.of(
                        HealthProfile.Condition.PREGNANCY,
                        HealthProfile.Condition.DIABETES,
                        HealthProfile.Condition.PREGNANCY),
                null);

        assertThat(profile.conditions())
                .containsExactly(HealthProfile.Condition.DIABETES, HealthProfile.Condition.PREGNANCY);
    }

    @Test
    void semCondicoesQuerDizerNenhuma() {
        assertThat(health(List.of(), null).conditions()).isEmpty();
        assertThat(health(null, null).conditions()).isEmpty();
    }

    @Test
    void condicaoVaziaNaListaERecusada() {
        assertThatThrownBy(() -> health(Arrays.asList(HealthProfile.Condition.DIABETES, null), null))
                .isInstanceOf(InvalidProfileException.class)
                .extracting("field")
                .isEqualTo("conditions");
    }

    @Test
    void textoLivreChegaSemEspacosNasPontas() {
        assertThat(health(List.of(), "  joelho operado em 2025  ").notes()).isEqualTo("joelho operado em 2025");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void textoLivreVazioFicaSemTexto(String notes) {
        assertThat(health(List.of(), notes).notes()).isNull();
    }

    @Test
    void textoLivreDeAte1000CaracteresEAceitoEAcimaERecusadoSemRepetirOTexto() {
        assertThat(health(List.of(), "a".repeat(1000)).notes()).hasSize(1000);

        String tooLong = "segredo".repeat(143); // 1001 caracteres
        assertThatThrownBy(() -> health(List.of(), tooLong))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageNotContaining("segredo")
                .extracting("field")
                .isEqualTo("notes");
    }

    @Test
    void condicoesNaoPodemSerAlteradasDepoisDeCriadas() {
        HealthProfile profile = health(List.of(HealthProfile.Condition.DIABETES), null);

        assertThatThrownBy(() -> profile.conditions().add(HealthProfile.Condition.OTHER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void perfilDeSaudeSemContaERecusado() {
        assertThatThrownBy(() -> HealthProfile.of(" ", List.of(), null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
