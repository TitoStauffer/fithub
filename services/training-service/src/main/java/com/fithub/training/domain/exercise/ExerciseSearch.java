package com.fithub.training.domain.exercise;

import java.util.List;

/**
 * Busca no catálogo e nos exercícios próprios (DEV-0055, FR-23): filtros opcionais que se combinam (E lógico),
 * em páginas ordenadas por nome.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: página padrão de 20 itens, máximo de 50; o nome busca
 * por trecho, sem diferenciar maiúsculas nem acentos.
 *
 * @param name trecho do nome, ou {@code null} para não filtrar
 * @param muscleGroup grupo muscular, ou {@code null}
 * @param equipment equipamento, ou {@code null}
 * @param page página, a partir de 0
 * @param size itens por página, de 1 a {@value #MAX_SIZE}
 */
public record ExerciseSearch(String name, MuscleGroup muscleGroup, Equipment equipment, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public ExerciseSearch {
        name = name == null || name.isBlank() ? null : name.strip();
        if (name != null && name.length() > Exercise.MAX_NAME_LENGTH) {
            throw new InvalidExerciseException("name", "O nome buscado pode ter até 120 caracteres.");
        }
        if (page < 0) {
            throw new InvalidExerciseException("page", "A página começa em 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidExerciseException("size", "O tamanho da página vai de 1 a 50.");
        }
    }

    /** Uma página do resultado, com o total de exercícios que atendem à busca. */
    public record Page(List<Exercise> items, long total) {

        public Page {
            items = List.copyOf(items);
        }
    }
}
