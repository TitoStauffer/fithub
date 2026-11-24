package com.fithub.training.domain.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Série registrada pelo aluno (DEV-0066, FR-48): carga e repetições reais de uma série de um item da divisão.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: carga de 0 a 1000 kg em passos de 0,25; repetições de 0 a
 * 300 (0 = não completou); até 20 séries por item.
 *
 * @param id gerado pelo app, para registrar de novo sem duplicar
 * @param exerciseId o exercício feito: o principal do item ou uma alternativa prevista
 */
public record WorkoutSet(
        UUID id,
        UUID sessionId,
        int itemPosition,
        int setNumber,
        UUID exerciseId,
        BigDecimal loadKg,
        int reps,
        Instant recordedAt,
        Instant updatedAt) {

    public static final int MAX_SET_NUMBER = 20;
    public static final BigDecimal MAX_LOAD_KG = new BigDecimal("1000");
    public static final int MAX_REPS = 300;

    public WorkoutSet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(exerciseId, "exerciseId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (itemPosition < 0) {
            throw new InvalidSetException("itemPosition", "Informe a posição do exercício na divisão.");
        }
        if (setNumber < 1 || setNumber > MAX_SET_NUMBER) {
            throw new InvalidSetException("setNumber", "A série vai de 1 a 20.");
        }
        loadKg = checkedLoad(loadKg);
        if (reps < 0 || reps > MAX_REPS) {
            throw new InvalidSetException("reps", "As repetições vão de 0 a 300.");
        }
    }

    private static BigDecimal checkedLoad(BigDecimal loadKg) {
        if (loadKg == null) {
            throw new InvalidSetException("loadKg", "Informe a carga (0 para peso do corpo).");
        }
        boolean quarterStep =
                loadKg.multiply(BigDecimal.valueOf(4)).stripTrailingZeros().scale() <= 0;
        if (loadKg.signum() < 0 || loadKg.compareTo(MAX_LOAD_KG) > 0 || !quarterStep) {
            throw new InvalidSetException("loadKg", "A carga vai de 0 a 1000 kg, em passos de 0,25.");
        }
        return loadKg.setScale(2);
    }

    /** A mesma série com a carga e as repetições corrigidas. */
    public WorkoutSet correct(UUID newExerciseId, BigDecimal newLoadKg, int newReps, Instant now) {
        return new WorkoutSet(
                id, sessionId, itemPosition, setNumber, newExerciseId, newLoadKg, newReps, recordedAt, now);
    }
}
