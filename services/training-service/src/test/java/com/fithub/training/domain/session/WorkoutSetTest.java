package com.fithub.training.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras da série registrada e do prazo de correção (DEV-0066). */
class WorkoutSetTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static WorkoutSet set(int setNumber, String load, int reps) {
        return new WorkoutSet(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                setNumber,
                UUID.randomUUID(),
                load == null ? null : new BigDecimal(load),
                reps,
                NOW,
                NOW);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidSetException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void cargaEmPassosDe025ERepeticoesDe0A300() {
        assertThat(set(1, "42.25", 10).loadKg()).isEqualByComparingTo("42.25");
        assertThat(set(1, "0", 0).reps()).isZero();
        assertThat(set(20, "1000", 300).setNumber()).isEqualTo(20);
        for (String load : new String[] {"42.3", "-0.25", "1000.25"}) {
            assertInvalid("loadKg", () -> set(1, load, 10));
        }
        assertInvalid("loadKg", () -> set(1, null, 10));
        assertInvalid("reps", () -> set(1, "40", 301));
        assertInvalid("reps", () -> set(1, "40", -1));
        assertInvalid("setNumber", () -> set(0, "40", 10));
        assertInvalid("setNumber", () -> set(21, "40", 10));
    }

    @Test
    void corrigirMantemAPosicaoEOHorarioDoRegistro() {
        WorkoutSet original = set(2, "40", 10);
        Instant later = NOW.plusSeconds(60);

        WorkoutSet corrected = original.correct(original.exerciseId(), new BigDecimal("42.5"), 8, later);

        assertThat(corrected.id()).isEqualTo(original.id());
        assertThat(corrected.setNumber()).isEqualTo(2);
        assertThat(corrected.loadKg()).isEqualByComparingTo("42.5");
        assertThat(corrected.reps()).isEqualTo(8);
        assertThat(corrected.recordedAt()).isEqualTo(NOW);
        assertThat(corrected.updatedAt()).isEqualTo(later);
    }

    @Test
    void prazoDeCorrecaoVaiAteOFimDoDiaDoTreinoNoFusoDeSaoPaulo() {
        // 23h de 24/11 em São Paulo (02h UTC do dia 25).
        Instant lateEvening = Instant.parse("2026-11-25T02:00:00Z");
        WorkoutSession session = new WorkoutSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, lateEvening, null);

        assertThat(session.correctable(Instant.parse("2026-11-25T02:59:00Z"), SAO_PAULO))
                .isTrue();
        // Meia-noite e um minuto em São Paulo: já é outro dia.
        assertThat(session.correctable(Instant.parse("2026-11-25T03:01:00Z"), SAO_PAULO))
                .isFalse();
        assertThat(session.finish(lateEvening).correctable(lateEvening.plusSeconds(60), SAO_PAULO))
                .isTrue();
    }
}
