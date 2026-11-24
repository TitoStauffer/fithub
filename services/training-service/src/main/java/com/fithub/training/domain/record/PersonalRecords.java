package com.fithub.training.domain.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Recordes pessoais por exercício (DEV-0068, FR-53, FR-54): a maior carga e o maior 1RM estimado, calculados das séries
 * registradas e dos lançamentos manuais.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: maior carga é a maior carga com ao menos 1 repetição; o 1RM
 * estimado usa a fórmula de Epley ({@code carga × (1 + reps/30)}), só de 1 a 12 repetições (com 1, vale a própria
 * carga). No empate, vale a marca mais antiga.
 */
public final class PersonalRecords {

    public static final int MAX_REPS_FOR_ESTIMATE = 12;

    private PersonalRecords() {}

    /** De onde veio a marca: uma série registrada ou um lançamento manual. */
    public enum Source {
        SET,
        MANUAL
    }

    /**
     * Uma marca feita: carga e repetições numa data.
     *
     * @param sourceId o id da série ou do lançamento manual
     */
    public record Performance(
            UUID exerciseId, BigDecimal loadKg, int reps, LocalDate date, Source source, UUID sourceId) {}

    /**
     * O recorde de um tipo: o valor e a marca que o deu.
     *
     * @param valueKg a carga (maior carga) ou o 1RM estimado
     */
    public record Mark(BigDecimal valueKg, Performance performance) {}

    /** Os recordes de um exercício; o 1RM estimado falta quando nenhuma marca tem de 1 a 12 repetições. */
    public record ExerciseRecords(UUID exerciseId, Mark maxLoad, Optional<Mark> estimatedOneRepMax) {}

    /** 1RM estimado por Epley, com duas casas; vazio fora de 1 a 12 repetições. */
    public static Optional<BigDecimal> estimatedOneRepMax(BigDecimal loadKg, int reps) {
        if (reps < 1 || reps > MAX_REPS_FOR_ESTIMATE) {
            return Optional.empty();
        }
        if (reps == 1) {
            return Optional.of(loadKg.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal factor =
                BigDecimal.ONE.add(BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP));
        return Optional.of(loadKg.multiply(factor).setScale(2, RoundingMode.HALF_UP));
    }

    /** Os recordes de cada exercício, na ordem em que o exercício aparece nas marcas. */
    public static List<ExerciseRecords> of(List<Performance> performances) {
        Map<UUID, List<Performance>> byExercise = new LinkedHashMap<>();
        for (Performance performance : performances) {
            if (performance.reps() >= 1) {
                byExercise
                        .computeIfAbsent(performance.exerciseId(), id -> new ArrayList<>())
                        .add(performance);
            }
        }
        // No empate, a marca mais antiga: quem chegou primeiro ao valor.
        Comparator<Mark> best = Comparator.comparing(Mark::valueKg)
                .thenComparing(mark -> mark.performance().date(), Comparator.reverseOrder());
        return byExercise.entrySet().stream()
                .map(entry -> new ExerciseRecords(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(performance -> new Mark(performance.loadKg(), performance))
                                .max(best)
                                .orElseThrow(),
                        entry.getValue().stream()
                                .flatMap(performance -> estimatedOneRepMax(performance.loadKg(), performance.reps())
                                        .map(value -> new Mark(value, performance))
                                        .stream())
                                .max(best)))
                .toList();
    }
}
