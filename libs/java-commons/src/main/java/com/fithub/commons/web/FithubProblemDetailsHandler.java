package com.fithub.commons.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handler global de erros no formato Problem Details (RFC 9457). As exceções do Spring MVC ficam com
 * o tratamento padrão da classe base; validação ganha a lista de campos; recusa de acesso vira 403;
 * qualquer outra exceção vira 500 genérico, com o detalhe só no log (nunca na resposta).
 */
@RestControllerAdvice
public class FithubProblemDetailsHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FithubProblemDetailsHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(FithubProblemDetailsHandler::fieldError)
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    // @PreAuthorize recusa dentro do MVC: sem este handler, o tratamento genérico abaixo devolveria 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Você não tem permissão para usar este recurso.");
        problem.setTitle("Acesso negado");
        return problem;
    }

    // Autenticação é responsabilidade do filtro de segurança: devolve a exceção para ele responder 401.
    @ExceptionHandler(AuthenticationException.class)
    public void rethrowAuthentication(AuthenticationException exception) {
        throw exception;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Erro inesperado ao processar a requisição", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente.");
        problem.setTitle("Erro interno");
        return problem;
    }

    private static Map<String, String> fieldError(FieldError error) {
        String message = error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage();
        return Map.of("field", error.getField(), "message", message);
    }
}
