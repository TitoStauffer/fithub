package com.fithub.nutrition.domain.food;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alimento da base da nutricionista (DEV-0074, FR-35, FR-37, FR-38): da Tabela TACO (compartilhado) ou próprio de uma
 * nutricionista (só dela, BR-13). Os valores nutricionais são por 100 da unidade base (100 g ou 100 ml).
 *
 * @param ownerId o {@code sub} da nutricionista dona (ADR-009); vazio na TACO
 * @param source fonte e id externo do item importado (para a importação ser reexecutável); vazio no próprio
 */
public record Food(
        UUID id,
        Kind kind,
        UUID ownerId,
        String name,
        BaseUnit baseUnit,
        Nutrients per100,
        boolean vegan,
        Source source,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 120;

    /** Da Tabela TACO (para todas as nutricionistas) ou próprio de uma nutricionista. */
    public enum Kind {
        TACO,
        OWN
    }

    /** A unidade das quantidades: gramas ou mililitros (FR-37). */
    public enum BaseUnit {
        G,
        ML
    }

    /** Origem do item importado: a fonte (ex.: a TACO) e o id do item nela. */
    public record Source(String name, String externalId) {

        public Source {
            if (name == null || name.isBlank() || externalId == null || externalId.isBlank()) {
                throw new InvalidFoodException("source", "Informe a fonte e o id externo do item importado.");
            }
        }
    }

    /**
     * Energia e macronutrientes por 100 da unidade base.
     *
     * @param fiberG opcional: a TACO não traz fibra para todo alimento
     */
    public record Nutrients(
            BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG, BigDecimal fiberG) {

        public static final BigDecimal MAX_ENERGY_KCAL = new BigDecimal("900");
        public static final BigDecimal MAX_GRAMS = new BigDecimal("100");

        public Nutrients {
            energyKcal = between("energyKcal", energyKcal, MAX_ENERGY_KCAL, 1, "A energia vai de 0 a 900 kcal.");
            proteinG = between("proteinG", proteinG, MAX_GRAMS, 2, "A proteína vai de 0 a 100 g.");
            carbsG = between("carbsG", carbsG, MAX_GRAMS, 2, "O carboidrato vai de 0 a 100 g.");
            fatG = between("fatG", fatG, MAX_GRAMS, 2, "A gordura vai de 0 a 100 g.");
            if (fiberG != null) {
                fiberG = between("fiberG", fiberG, MAX_GRAMS, 2, "A fibra vai de 0 a 100 g.");
            }
            // Em 100 g de alimento não cabem mais de 100 g de macronutrientes.
            if (proteinG.add(carbsG).add(fatG).compareTo(MAX_GRAMS) > 0) {
                throw new InvalidFoodException(
                        "per100", "Proteína, carboidrato e gordura somam no máximo 100 g por 100 g.");
            }
        }

        private static BigDecimal between(String field, BigDecimal value, BigDecimal max, int scale, String message) {
            if (value == null) {
                throw new InvalidFoodException(field, message);
            }
            if (value.signum() < 0 || value.compareTo(max) > 0) {
                throw new InvalidFoodException(field, message);
            }
            return value.setScale(scale, RoundingMode.HALF_UP);
        }
    }

    public Food {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (kind == null) {
            throw new InvalidFoodException("kind", "Informe o tipo do alimento.");
        }
        if ((kind == Kind.OWN) != (ownerId != null)) {
            throw new InvalidFoodException("ownerId", "Só o alimento próprio tem dona.");
        }
        if ((kind == Kind.TACO) != (source != null)) {
            throw new InvalidFoodException("source", "Só o alimento importado tem a fonte.");
        }
        name = name == null ? null : name.strip();
        if (name == null || name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidFoodException("name", "O nome precisa ter de 2 a 120 caracteres.");
        }
        if (baseUnit == null) {
            throw new InvalidFoodException("baseUnit", "Informe a unidade base (g ou ml).");
        }
        if (per100 == null) {
            throw new InvalidFoodException("per100", "Informe os valores nutricionais por 100.");
        }
    }

    /** A TACO é de todas as nutricionistas; o próprio, só da dona (BR-13). */
    public boolean visibleTo(UUID nutritionistId) {
        return kind == Kind.TACO || ownerId.equals(nutritionistId);
    }
}
