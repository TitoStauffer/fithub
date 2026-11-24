package com.fithub.training.domain.exercise;

/**
 * O exercício não existe ou é próprio de outro personal: a resposta é a mesma, para não revelar que ele existe
 * (BR-13).
 */
public final class ExerciseNotFoundException extends RuntimeException {

    public ExerciseNotFoundException() {
        super("Exercício não encontrado.");
    }
}
