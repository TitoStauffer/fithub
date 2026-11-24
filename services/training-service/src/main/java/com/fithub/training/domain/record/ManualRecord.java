package com.fithub.training.domain.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Recorde lançado à mão pelo aluno (DEV-0068, FR-54): uma marca feita fora das séries registradas, marcada como manual.
 *
 * @param clientId o {@code sub} do aluno (ADR-009)
 * @param performedOn quando a marca foi feita; não pode ser no futuro
 */
public record ManualRecord(
        UUID id,
        UUID clientId,
        UUID exerciseId,
        BigDecimal loadKg,
        int reps,
        LocalDate performedOn,
        Instant createdAt) {

    public static final BigDecimal MAX_LOAD_KG = new BigDecimal("1000");
    public static final int MAX_REPS = 300;

    public ManualRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (exerciseId == null) {
            throw new InvalidRecordException("exerciseId", "Escolha o exercício.");
        }
        loadKg = checkedLoad(loadKg);
        if (reps < 1 || reps > MAX_REPS) {
            throw new InvalidRecordException("reps", "As repetições vão de 1 a 300.");
        }
        if (performedOn == null) {
            throw new InvalidRecordException("performedOn", "Informe a data.");
        }
    }

    private static BigDecimal checkedLoad(BigDecimal loadKg) {
        if (loadKg == null) {
            throw new InvalidRecordException("loadKg", "Informe a carga.");
        }
        boolean quarterStep =
                loadKg.multiply(BigDecimal.valueOf(4)).stripTrailingZeros().scale() <= 0;
        if (loadKg.signum() <= 0 || loadKg.compareTo(MAX_LOAD_KG) > 0 || !quarterStep) {
            throw new InvalidRecordException("loadKg", "A carga vai de 0,25 a 1000 kg, em passos de 0,25.");
        }
        return loadKg.setScale(2);
    }

    /**
     * Novo lançamento manual.
     *
     * @param today o dia de hoje no fuso do aluno: a data da marca não pode passar dele
     */
    public static ManualRecord of(
            UUID clientId,
            UUID exerciseId,
            BigDecimal loadKg,
            Integer reps,
            LocalDate performedOn,
            LocalDate today,
            Instant now) {
        if (reps == null) {
            throw new InvalidRecordException("reps", "Informe as repetições.");
        }
        if (performedOn != null && performedOn.isAfter(today)) {
            throw new InvalidRecordException("performedOn", "A data não pode ser no futuro.");
        }
        return new ManualRecord(UUID.randomUUID(), clientId, exerciseId, loadKg, reps, performedOn, now);
    }

    public boolean ownedBy(UUID client) {
        return clientId.equals(client);
    }

    /** A marca, para entrar no cálculo dos recordes. */
    public PersonalRecords.Performance performance() {
        return new PersonalRecords.Performance(
                exerciseId, loadKg, reps, performedOn, PersonalRecords.Source.MANUAL, id);
    }
}
