package com.fithub.training.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sequência dos treinos e finalização da sessão (DEV-0065). */
class WorkoutSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private static WorkoutSession finishedAt(int division) {
        return new WorkoutSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), division, NOW, NOW);
    }

    @Test
    void proximoTreinoGiraEmOrdemEVoltaAoInicio() {
        assertThat(WorkoutSession.nextDivision(null, 3)).isZero();
        assertThat(WorkoutSession.nextDivision(finishedAt(0), 3)).isEqualTo(1);
        assertThat(WorkoutSession.nextDivision(finishedAt(1), 3)).isEqualTo(2);
        assertThat(WorkoutSession.nextDivision(finishedAt(2), 3)).isZero();
        assertThat(WorkoutSession.nextDivision(finishedAt(0), 1)).isZero();
    }

    @Test
    void versaoComMenosDivisoesVoltaAoComecoPeloResto() {
        // A última foi a D (posição 3) e a versão nova tem só A e B.
        assertThat(WorkoutSession.nextDivision(finishedAt(3), 2)).isZero();
        assertThat(WorkoutSession.nextDivision(finishedAt(4), 3)).isEqualTo(2);
    }

    @Test
    void finalizarDeNovoNaoTrocaAData() {
        WorkoutSession open = new WorkoutSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, NOW, null);

        WorkoutSession finished = open.finish(NOW.plusSeconds(3600));
        assertThat(finished.finished()).isTrue();
        assertThat(finished.finish(NOW.plusSeconds(7200)).finishedAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(open.ownedBy(open.clientId())).isTrue();
        assertThat(open.ownedBy(UUID.randomUUID())).isFalse();
        assertThat(open.ownedBy(null)).isFalse();
    }

    @Test
    void comentarioESensacaoTemLimitesEFicamAoFinalizar() {
        WorkoutSession open = new WorkoutSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, NOW, null);

        WorkoutSession withFeedback = open.withFeedback("  Pesado.  ", 5);
        assertThat(withFeedback.comment()).isEqualTo("Pesado.");
        assertThat(withFeedback.finish(NOW.plusSeconds(60)).feeling()).isEqualTo(5);
        assertThat(withFeedback.finish(NOW.plusSeconds(60)).comment()).isEqualTo("Pesado.");
        assertThat(open.withFeedback(" ", null).comment()).isNull();
        for (int feeling : new int[] {0, 6}) {
            assertThatThrownBy(() -> open.withFeedback(null, feeling))
                    .isInstanceOfSatisfying(
                            InvalidSetException.class,
                            invalid -> assertThat(invalid.field()).isEqualTo("feeling"));
        }
        assertThatThrownBy(() -> open.withFeedback("a".repeat(501), 3))
                .isInstanceOfSatisfying(
                        InvalidSetException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("comment"));
    }
}
