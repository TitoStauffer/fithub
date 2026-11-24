package com.fithub.nutrition.domain.food;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Medida caseira de um alimento (DEV-0076, FR-37): colher de sopa, unidade, fatia… e quanto ela vale na unidade base
 * do alimento (gramas ou ml). É da nutricionista que cadastrou e só ela vê (BR-13).
 *
 * @param ownerId o {@code sub} da nutricionista (ADR-009)
 * @param amount quanto uma medida vale na unidade base, de 0,1 a 2000
 */
public record FoodMeasure(UUID id, UUID foodId, UUID ownerId, String name, BigDecimal amount, Instant createdAt) {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 40;
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.1");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("2000");
    /** Medidas de uma nutricionista por alimento. */
    public static final int MAX_PER_FOOD = 10;

    public FoodMeasure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(foodId, "foodId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
        name = name == null ? null : name.strip();
        if (name == null || name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidFoodException("name", "O nome da medida precisa ter de 2 a 40 caracteres.");
        }
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidFoodException("amount", "A medida vale de 0,1 a 2000 g (ou ml).");
        }
        amount = amount.setScale(1, RoundingMode.HALF_UP);
    }

    public boolean ownedBy(UUID nutritionistId) {
        return ownerId.equals(nutritionistId);
    }

    /** Quanto {@code quantity} medidas valem na unidade base: 2 colheres de 25 g são 50 g. */
    public BigDecimal toBaseUnit(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidFoodException("quantity", "Informe uma quantidade maior que zero.");
        }
        return amount.multiply(quantity).setScale(1, RoundingMode.HALF_UP);
    }
}
