package com.fithub.training.interfaces.rest;

import com.fithub.training.domain.exercise.ExerciseArchivedException;
import com.fithub.training.domain.exercise.ExerciseNotFoundException;
import com.fithub.training.domain.exercise.InvalidExerciseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Respostas de erro das rotas de exercício, em Problem Details. Cada controller declara os próprios
 * {@code @ExceptionHandler} e delega aqui: tratador local vence o genérico da java-commons.
 */
final class ExerciseProblems {

    private ExerciseProblems() {}

    static ProblemDetail invalid(InvalidExerciseException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    static ProblemDetail notFound(ExerciseNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Exercício não encontrado");
        return problem;
    }

    static ProblemDetail archived(ExerciseArchivedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Exercício arquivado");
        return problem;
    }
}
