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
 * @param recipe os ingredientes e o rendimento, quando o alimento próprio é uma receita (DEV-0077)
 * @param archivedAt quando a dona arquivou o próprio: sai da busca, mas continua nos planos (DEV-0077)
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
        Instant updatedAt,
        Recipe recipe,
        Instant archivedAt) {

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
        name = checkedName(name);
        if (baseUnit == null) {
            throw new InvalidFoodException("baseUnit", "Informe a unidade base (g ou ml).");
        }
        if (per100 == null) {
            throw new InvalidFoodException("per100", "Informe os valores nutricionais por 100.");
        }
        requireOwnForRecipeAndArchive(kind, baseUnit, recipe, archivedAt);
    }

    private static String checkedName(String name) {
        String stripped = name == null ? null : name.strip();
        if (stripped == null || stripped.length() < MIN_NAME_LENGTH || stripped.length() > MAX_NAME_LENGTH) {
            throw new InvalidFoodException("name", "O nome precisa ter de 2 a 120 caracteres.");
        }
        return stripped;
    }

    /** Receita e arquivamento são só do alimento próprio; a receita é sempre em gramas. */
    private static void requireOwnForRecipeAndArchive(Kind kind, BaseUnit baseUnit, Recipe recipe, Instant archivedAt) {
        if (recipe != null && (kind != Kind.OWN || baseUnit != BaseUnit.G)) {
            throw new InvalidFoodException("recipe", "Só o alimento próprio em gramas pode ser receita.");
        }
        if (archivedAt != null && kind != Kind.OWN) {
            throw new InvalidFoodException("archivedAt", "Só o alimento próprio é arquivado.");
        }
    }

    /** Alimento que não é receita nem está arquivado (o da TACO e o próprio novo). */
    public Food(
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
        this(id, kind, ownerId, name, baseUnit, per100, vegan, source, createdAt, updatedAt, null, null);
    }

    public boolean archived() {
        return archivedAt != null;
    }

    public boolean isRecipe() {
        return recipe != null;
    }

    /** A dona arquiva o próprio; arquivar de novo não muda a data. */
    public Food archive(Instant now) {
        if (kind != Kind.OWN) {
            throw new InvalidFoodException("archivedAt", "Só o alimento próprio é arquivado.");
        }
        Instant when = archivedAt == null ? now : archivedAt;
        return new Food(id, kind, ownerId, name, baseUnit, per100, vegan, source, createdAt, updatedAt, recipe, when);
    }

    /** A dona do próprio; a TACO não tem dona. */
    public boolean ownedBy(UUID nutritionistId) {
        return kind == Kind.OWN && ownerId.equals(nutritionistId);
    }

    /**
     * Energia e nutrientes de uma quantidade (na unidade base), arredondados a 1 casa; a fibra falta se o alimento não
     * traz. Sem limite de 100: é o quanto há na porção.
     */
    public record Amounts(
            BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG, BigDecimal fiberG) {

        /** Nada ainda: o ponto de partida da soma (refeição sem alimento soma zero). */
        public static final Amounts ZERO = new Amounts(
                BigDecimal.ZERO.setScale(1),
                BigDecimal.ZERO.setScale(1),
                BigDecimal.ZERO.setScale(1),
                BigDecimal.ZERO.setScale(1),
                BigDecimal.ZERO.setScale(1));

        /**
         * A soma com outra porção (DEV-0081): a refeição soma os itens e o dia soma as refeições. A fibra falta se
         * faltar em alguma parte, como na receita: somar só o que se sabe daria um total menor que o real.
         */
        public Amounts plus(Amounts other) {
            return new Amounts(
                    energyKcal.add(other.energyKcal),
                    proteinG.add(other.proteinG),
                    carbsG.add(other.carbsG),
                    fatG.add(other.fatG),
                    fiberG == null || other.fiberG == null ? null : fiberG.add(other.fiberG));
        }
    }

    /** Os nutrientes de {@code quantity} gramas (ou ml) do alimento (DEV-0076): o valor por 100 vezes a quantidade. */
    public Amounts nutrientsIn(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidFoodException("quantity", "Informe uma quantidade maior que zero.");
        }
        return new Amounts(
                portion(per100.energyKcal(), quantity),
                portion(per100.proteinG(), quantity),
                portion(per100.carbsG(), quantity),
                portion(per100.fatG(), quantity),
                per100.fiberG() == null ? null : portion(per100.fiberG(), quantity));
    }

    private static BigDecimal portion(BigDecimal valuePer100, BigDecimal quantity) {
        return valuePer100.multiply(quantity).divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
    }

    /** A TACO é de todas as nutricionistas; o próprio, só da dona (BR-13). */
    public boolean visibleTo(UUID nutritionistId) {
        return kind == Kind.TACO || ownerId.equals(nutritionistId);
    }
}
