package com.fithub.training.domain.plan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Exercício prescrito numa divisão da ficha (DEV-0060, FR-30, FR-31): séries, repetições fixas ou em faixa, carga
 * sugerida, descanso, observação, técnica e até 3 alternativas.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: séries de 1 a 20; repetições de 1 a 100 (fixas quando o
 * mínimo é igual ao máximo); carga de 0 a 1000 kg em passos de 0,5; descanso de 0 a 600 segundos; observação até 500
 * caracteres. Bi-set e tri-set são a técnica do item: itens seguidos com a mesma técnica formam o conjunto na tela.
 *
 * @param loadKg carga sugerida, ou {@code null}
 * @param restSeconds descanso entre as séries, ou {@code null}
 * @param technique técnica opcional, ou {@code null}
 * @param alternatives exercícios que substituem este (máquina ocupada, por exemplo), na ordem
 */
public record PlanItem(
        UUID exerciseId,
        int sets,
        int repsMin,
        int repsMax,
        BigDecimal loadKg,
        Integer restSeconds,
        String note,
        Technique technique,
        List<UUID> alternatives) {

    /** Técnica opcional do item. */
    public enum Technique {
        BI_SET,
        TRI_SET,
        DROP_SET,
        REST_PAUSE
    }

    public static final int MAX_SETS = 20;
    public static final int MAX_REPS = 100;
    public static final BigDecimal MAX_LOAD_KG = new BigDecimal("1000");
    public static final int MAX_REST_SECONDS = 600;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final int MAX_ALTERNATIVES = 3;

    public PlanItem {
        if (exerciseId == null) {
            throw new InvalidPlanException("exerciseId", "Escolha o exercício.");
        }
        requireBetween("sets", sets, 1, MAX_SETS, "As séries vão de 1 a 20.");
        requireBetween("repsMin", repsMin, 1, MAX_REPS, "As repetições vão de 1 a 100.");
        requireBetween("repsMax", repsMax, repsMin, MAX_REPS, "O máximo da faixa vai do mínimo até 100.");
        loadKg = checkedLoad(loadKg);
        if (restSeconds != null) {
            requireBetween("restSeconds", restSeconds, 0, MAX_REST_SECONDS, "O descanso vai de 0 a 600 segundos.");
        }
        note = checkedNote(note);
        alternatives = checkedAlternatives(exerciseId, alternatives);
    }

    private static void requireBetween(String field, int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new InvalidPlanException(field, message);
        }
    }

    private static String checkedNote(String note) {
        String stripped = note == null || note.isBlank() ? null : note.strip();
        if (stripped != null && stripped.length() > MAX_NOTE_LENGTH) {
            throw new InvalidPlanException("note", "A observação pode ter até 500 caracteres.");
        }
        return stripped;
    }

    private static BigDecimal checkedLoad(BigDecimal loadKg) {
        if (loadKg == null) {
            return null;
        }
        boolean halfStep = loadKg.multiply(BigDecimal.TWO).stripTrailingZeros().scale() <= 0;
        if (loadKg.signum() < 0 || loadKg.compareTo(MAX_LOAD_KG) > 0 || !halfStep) {
            throw new InvalidPlanException("loadKg", "A carga vai de 0 a 1000 kg, em passos de 0,5.");
        }
        return loadKg.setScale(1);
    }

    private static List<UUID> checkedAlternatives(UUID exerciseId, List<UUID> alternatives) {
        if (alternatives == null) {
            return List.of();
        }
        if (alternatives.size() > MAX_ALTERNATIVES) {
            throw new InvalidPlanException("alternatives", "Cada exercício tem até 3 alternativas.");
        }
        var seen = new HashSet<UUID>();
        for (int index = 0; index < alternatives.size(); index++) {
            UUID alternative = alternatives.get(index);
            if (alternative == null || alternative.equals(exerciseId) || !seen.add(alternative)) {
                throw new InvalidPlanException(
                        "alternatives[" + index + "]", "A alternativa precisa ser outro exercício, sem repetir.");
            }
        }
        return List.copyOf(alternatives);
    }

    /** Repetições fixas (mínimo igual ao máximo) ou faixa, como 8–12. */
    public boolean repsRange() {
        return repsMin != repsMax;
    }

    /** Todos os exercícios do item: o principal e as alternativas. */
    public List<UUID> exerciseIds() {
        var ids = new ArrayList<UUID>();
        ids.add(exerciseId);
        ids.addAll(alternatives);
        return ids;
    }

    /**
     * O que chega da tela, com os campos ainda opcionais.
     *
     * @param repsMax {@code null} para repetições fixas
     * @param technique nome da técnica, ou {@code null}
     */
    public record Draft(
            UUID exerciseId,
            Integer sets,
            Integer repsMin,
            Integer repsMax,
            BigDecimal loadKg,
            Integer restSeconds,
            String note,
            String technique,
            List<UUID> alternatives) {

        public PlanItem toItem() {
            if (sets == null) {
                throw new InvalidPlanException("sets", "Informe as séries.");
            }
            if (repsMin == null) {
                throw new InvalidPlanException("repsMin", "Informe as repetições.");
            }
            return new PlanItem(
                    exerciseId,
                    sets,
                    repsMin,
                    Objects.requireNonNullElse(repsMax, repsMin),
                    loadKg,
                    restSeconds,
                    note,
                    parsedTechnique(),
                    alternatives);
        }

        private Technique parsedTechnique() {
            if (technique == null || technique.isBlank()) {
                return null;
            }
            try {
                return Technique.valueOf(technique.strip());
            } catch (IllegalArgumentException unknown) {
                throw new InvalidPlanException("technique", "Técnica fora da lista.");
            }
        }
    }
}
