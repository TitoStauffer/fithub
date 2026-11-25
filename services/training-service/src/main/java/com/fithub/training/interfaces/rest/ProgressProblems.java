package com.fithub.training.interfaces.rest;

import com.fithub.training.domain.progress.InvalidProgressException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** O 400 das consultas de evolução, com o campo, igual nas rotas do personal e do aluno. */
final class ProgressProblems {

    private ProgressProblems() {}

    static ProblemDetail invalid(InvalidProgressException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }
}
