package com.fithub.client.interfaces.rest;

import com.fithub.client.domain.profile.InvalidProfileException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Respostas de erro comuns aos controllers de perfil, no formato do handler global (errors[]). */
final class ProfileProblems {

    private ProfileProblems() {}

    /** A mensagem é a do domínio, escrita para a pessoa; nunca repete o valor enviado. */
    static ProblemDetail invalid(InvalidProfileException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }
}
