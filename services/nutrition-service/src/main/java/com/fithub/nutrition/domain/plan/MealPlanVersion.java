package com.fithub.nutrition.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O conteúdo de uma versão do plano (DEV-0081): nome, início, observação geral (FR-45) e as refeições (1 a 10).
 *
 * @param publishedAt quando a versão foi publicada; vazio no rascunho
 */
public record MealPlanVersion(
        UUID id,
        int number,
        String name,
        LocalDate startsOn,
        String note,
        List<Meal> meals,
        Instant publishedAt,
        Instant createdAt) {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final int MAX_MEALS = 10;

    public MealPlanVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        name = name == null ? "" : name.strip();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidMealPlanException("name", "O nome do plano precisa ter de 2 a 80 caracteres.");
        }
        if (startsOn == null) {
            throw new InvalidMealPlanException("startsOn", "Informe a data de início.");
        }
        note = note == null || note.isBlank() ? null : note.strip();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new InvalidMealPlanException("note", "A observação do plano pode ter até 500 caracteres.");
        }
        requireMealCount(meals);
        meals = List.copyOf(meals);
    }

    public boolean published() {
        return publishedAt != null;
    }

    /** As refeições informadas, já validadas e na ordem. */
    public static List<Meal> meals(List<Meal.Draft> drafts) {
        requireMealCount(drafts);
        return Meal.of(drafts);
    }

    /** De 1 a 10 refeições. */
    public static void requireMealCount(List<?> meals) {
        if (meals == null || meals.isEmpty() || meals.size() > MAX_MEALS) {
            throw new InvalidMealPlanException("meals", "O plano tem de 1 a 10 refeições.");
        }
    }
}
