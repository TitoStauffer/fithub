package com.fithub.nutrition.domain.food;

import java.util.List;

/**
 * Busca de alimentos da nutricionista (DEV-0078, FR-35, FR-38): a TACO e os próprios dela, não arquivados, em páginas
 * ordenadas pelo nome (no empate, o próprio antes da TACO).
 *
 * @param name trecho do nome (sem diferenciar maiúsculas nem acentos), ou {@code null} para não filtrar
 * @param vegan {@code true} para só os veganos, {@code false} para só os não veganos, {@code null} para todos
 * @param page página, a partir de 0
 * @param size itens por página, de 1 a {@value #MAX_SIZE}
 */
public record FoodSearch(String name, Boolean vegan, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public FoodSearch {
        name = name == null || name.isBlank() ? null : name.strip();
        if (name != null && name.length() > Food.MAX_NAME_LENGTH) {
            throw new InvalidFoodException("name", "O nome buscado pode ter até 120 caracteres.");
        }
        if (page < 0) {
            throw new InvalidFoodException("page", "A página começa em 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidFoodException("size", "O tamanho da página vai de 1 a 50.");
        }
    }

    /** Uma página do resultado, com o total de alimentos que atendem à busca. */
    public record Page(List<Food> items, long total) {

        public Page {
            items = List.copyOf(items);
        }
    }
}
