package com.fithub.training.domain.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Limites da busca de exercícios (DEV-0055): página a partir de 0, tamanho de 1 a 50 e nome até 120. */
class ExerciseSearchTest {

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidExerciseException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void tamanhoVaiDe1A50EPaginaComecaEm0() {
        assertThat(new ExerciseSearch(null, null, null, 0, 1).size()).isEqualTo(1);
        assertThat(new ExerciseSearch(null, null, null, 3, ExerciseSearch.MAX_SIZE).size())
                .isEqualTo(50);
        assertInvalid("size", () -> new ExerciseSearch(null, null, null, 0, 0));
        assertInvalid("size", () -> new ExerciseSearch(null, null, null, 0, 51));
        assertInvalid("page", () -> new ExerciseSearch(null, null, null, -1, 20));
    }

    @Test
    void nomeEmBrancoNaoFiltraENomeLongoDemaisERecusado() {
        assertThat(new ExerciseSearch("  ", null, null, 0, 20).name()).isNull();
        assertThat(new ExerciseSearch(" supino ", null, null, 0, 20).name()).isEqualTo("supino");
        assertInvalid("name", () -> new ExerciseSearch("a".repeat(121), null, null, 0, 20));
    }
}
