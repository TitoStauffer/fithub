package com.fithub.account.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Forma do erro que o serviço responde em {@code application/problem+json}, só para o contrato OpenAPI. O
 * {@code ProblemDetail} do Spring gera um schema com um objeto {@code properties}, mas o JSON real achata as
 * propriedades e traz {@code errors[]} no nível de cima (Code Review do PR #202).
 */
@Schema(name = "Problem", description = "Erro no formato Problem Details (RFC 9457).")
record ApiProblem(
        @Schema(description = "URI do tipo do erro.", example = "about:blank")
        String type,

        String title,
        int status,

        @Schema(description = "Mensagem para a pessoa, em português.")
        String detail,

        @Schema(nullable = true) String instance,

        @Schema(nullable = true, description = "Campos inválidos; presente só nas respostas 400.")
        List<FieldError> errors) {

    @Schema(name = "ProblemFieldError")
    record FieldError(String field, String message) {}
}
