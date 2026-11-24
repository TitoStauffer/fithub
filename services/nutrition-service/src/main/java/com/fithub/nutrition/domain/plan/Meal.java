package com.fithub.nutrition.domain.plan;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Refeição do plano (DEV-0081, FR-42, FR-45): nome, horário sugerido opcional, observação e os alimentos, na ordem.
 *
 * @param position a ordem no dia, a partir de 0
 */
public record Meal(int position, String name, LocalTime suggestedTime, String note, List<MealItem> items) {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final int MAX_ITEMS = 30;

    public Meal {
        name = name == null ? "" : name.strip();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidMealPlanException("name", "O nome da refeição precisa ter de 2 a 60 caracteres.");
        }
        note = note == null || note.isBlank() ? null : note.strip();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new InvalidMealPlanException("note", "A observação da refeição pode ter até 500 caracteres.");
        }
        items = items == null ? List.of() : List.copyOf(items);
        if (items.size() > MAX_ITEMS) {
            throw new InvalidMealPlanException("items", "Cada refeição tem até 30 alimentos.");
        }
    }

    /** O que a nutricionista informa para uma refeição; os itens já vêm com a quantidade resolvida. */
    public record Draft(String name, LocalTime suggestedTime, String note, List<MealItem> items) {}

    /** As refeições na ordem informada, com o caminho de cada erro ({@code meals[1].items[0].amount}). */
    static List<Meal> of(List<Draft> drafts) {
        List<Meal> meals = new ArrayList<>();
        for (int position = 0; position < drafts.size(); position++) {
            Draft draft = drafts.get(position);
            if (draft == null) {
                throw new InvalidMealPlanException("meals[" + position + "]", "Informe a refeição.");
            }
            try {
                meals.add(new Meal(position, draft.name(), draft.suggestedTime(), draft.note(), draft.items()));
            } catch (InvalidMealPlanException invalid) {
                throw invalid.under("meals[" + position + "]");
            }
        }
        return meals;
    }
}
