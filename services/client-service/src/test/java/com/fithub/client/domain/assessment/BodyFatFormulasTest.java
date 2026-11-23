package com.fithub.client.domain.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Valores de referência calculados à parte, com as fórmulas publicadas de Jackson & Pollock e de Siri. */
class BodyFatFormulasTest {

    /** Homem: peitoral 10, axilar 12, tríceps 11, subescapular 14, abdominal 20, suprailíaca 13, coxa 15. */
    private static final Skinfolds MAN = folds("10", "12", "11", "14", "20", "13", "15");

    /** Mulher: peitoral 9, axilar 11, tríceps 18, subescapular 15, abdominal 22, suprailíaca 16, coxa 26. */
    private static final Skinfolds WOMAN = folds("9", "11", "18", "15", "22", "16", "26");

    private static Skinfolds folds(String... mm) {
        BigDecimal[] values = new BigDecimal[mm.length];
        for (int i = 0; i < mm.length; i++) {
            values[i] = mm[i] == null ? null : new BigDecimal(mm[i]);
        }
        return new Skinfolds(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }

    @Test
    void pollock3EPollock7DoHomem() {
        assertThat(BodyFatFormulas.pollock3(Assessment.Sex.MALE, 30, MAN)).contains(new BigDecimal("13.6"));
        assertThat(BodyFatFormulas.pollock7(Assessment.Sex.MALE, 30, MAN)).contains(new BigDecimal("13.9"));
    }

    @Test
    void pollock3EPollock7DaMulher() {
        assertThat(BodyFatFormulas.pollock3(Assessment.Sex.FEMALE, 35, WOMAN)).contains(new BigDecimal("24.4"));
        assertThat(BodyFatFormulas.pollock7(Assessment.Sex.FEMALE, 35, WOMAN)).contains(new BigDecimal("23.6"));
    }

    @Test
    void cadaSexoUsaAsDobrasDoProprioProtocolo3() {
        // Só as dobras do homem: a mulher não tem tríceps e suprailíaca, então o Pollock 3 dela fica vazio.
        Skinfolds onlyMaleSites = folds("10", null, null, null, "20", null, "15");

        assertThat(BodyFatFormulas.pollock3(Assessment.Sex.MALE, 30, onlyMaleSites))
                .isPresent();
        assertThat(BodyFatFormulas.pollock3(Assessment.Sex.FEMALE, 30, onlyMaleSites))
                .isEmpty();
        assertThat(BodyFatFormulas.pollock7(Assessment.Sex.MALE, 30, onlyMaleSites))
                .isEmpty();
    }

    @Test
    void semSexoOuSemIdadeNaoHaResultado() {
        assertThat(BodyFatFormulas.pollock3(null, 30, MAN)).isEmpty();
        assertThat(BodyFatFormulas.pollock7(Assessment.Sex.MALE, null, MAN)).isEmpty();
    }

    @Test
    void idadeMudaOResultado() {
        assertThat(BodyFatFormulas.pollock7(Assessment.Sex.MALE, 50, MAN))
                .isNotEqualTo(BodyFatFormulas.pollock7(Assessment.Sex.MALE, 30, MAN));
    }
}
