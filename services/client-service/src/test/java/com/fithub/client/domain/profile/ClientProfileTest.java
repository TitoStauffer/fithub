package com.fithub.client.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientProfileTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-23");
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static ClientProfile profile(String name, LocalDate birthDate, int heightCm) {
        return ClientProfile.of(
                "conta-1",
                name,
                birthDate,
                ClientProfile.Gender.FEMALE,
                heightCm,
                ClientProfile.Goal.HEALTH,
                TODAY,
                NOW,
                NOW);
    }

    private static String fieldOf(Runnable action) {
        try {
            action.run();
        } catch (InvalidProfileException invalid) {
            return invalid.field();
        }
        throw new AssertionError("o perfil deveria ter sido recusado");
    }

    @Test
    void perfilValidoTiraEspacosDoNomeECalculaAIdade() {
        ClientProfile profile = profile("  Ana Cliente ", LocalDate.parse("1990-11-24"), 165);

        assertThat(profile.name()).isEqualTo("Ana Cliente");
        // Faz 36 anos amanhã: hoje ainda tem 35.
        assertThat(profile.ageOn(TODAY)).isEqualTo(35);
        assertThat(profile.ageOn(TODAY.plusDays(1))).isEqualTo(36);
    }

    @Test
    void exatamente18AnosHojeEAceito() {
        assertThat(profile("Ana", TODAY.minusYears(18), 165).ageOn(TODAY)).isEqualTo(18);
    }

    @Test
    void menorDe18AnosERecusadoEnquantoAQuestao05EstiverAberta() {
        assertThat(fieldOf(() -> profile("Ana", TODAY.minusYears(18).plusDays(1), 165)))
                .isEqualTo("birthDate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-23", "2030-01-01"})
    void nascimentoHojeOuNoFuturoERecusado(String birthDate) {
        // A mensagem é a do nascimento no passado, não a da idade mínima: é o que a pessoa precisa corrigir.
        assertThatThrownBy(() -> profile("Ana", LocalDate.parse(birthDate), 165))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("precisa estar no passado")
                .extracting("field")
                .isEqualTo("birthDate");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nomeVazioERecusado(String name) {
        assertThat(fieldOf(() -> profile(name, LocalDate.parse("1990-01-01"), 165)))
                .isEqualTo("name");
    }

    @Test
    void nomeAcimaDe200CaracteresERecusado() {
        assertThat(profile("a".repeat(200), LocalDate.parse("1990-01-01"), 165).name())
                .hasSize(200);
        assertThat(fieldOf(() -> profile("a".repeat(201), LocalDate.parse("1990-01-01"), 165)))
                .isEqualTo("name");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 99, 251, -170})
    void alturaForaDe100a250ERecusada(int heightCm) {
        assertThat(fieldOf(() -> profile("Ana", LocalDate.parse("1990-01-01"), heightCm)))
                .isEqualTo("heightCm");
    }

    @Test
    void limitesDaAlturaSaoAceitos() {
        assertThat(profile("Ana", LocalDate.parse("1990-01-01"), 100).heightCm())
                .isEqualTo(100);
        assertThat(profile("Ana", LocalDate.parse("1990-01-01"), 250).heightCm())
                .isEqualTo(250);
    }

    @Test
    void camposObrigatoriosAusentesSaoRecusadosComOCampo() {
        assertThat(fieldOf(() -> profile("Ana", null, 165))).isEqualTo("birthDate");
        assertThat(fieldOf(() -> ClientProfile.of(
                        "conta-1",
                        "Ana",
                        LocalDate.parse("1990-01-01"),
                        null,
                        165,
                        ClientProfile.Goal.HEALTH,
                        TODAY,
                        NOW,
                        NOW)))
                .isEqualTo("gender");
        assertThat(fieldOf(() -> ClientProfile.of(
                        "conta-1",
                        "Ana",
                        LocalDate.parse("1990-01-01"),
                        ClientProfile.Gender.MALE,
                        165,
                        null,
                        TODAY,
                        NOW,
                        NOW)))
                .isEqualTo("goal");
    }

    @Test
    void perfilSemContaERecusado() {
        assertThatThrownBy(() -> ClientProfile.of(
                        " ",
                        "Ana",
                        LocalDate.parse("1990-01-01"),
                        ClientProfile.Gender.MALE,
                        165,
                        ClientProfile.Goal.HEALTH,
                        TODAY,
                        NOW,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
