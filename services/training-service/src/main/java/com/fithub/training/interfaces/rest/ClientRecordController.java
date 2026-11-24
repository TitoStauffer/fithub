package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ClientRecords;
import com.fithub.training.domain.access.ClientNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Recordes do aluno vistos pelo personal vinculado (DEV-0068, FR-55). Sem vínculo ativo, 404 (BR-02). */
@RestController
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Recordes", description = "Recordes pessoais por exercício")
class ClientRecordController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ClientRecords clientRecords;

    ClientRecordController(ClientRecords clientRecords) {
        this.clientRecords = clientRecords;
    }

    @GetMapping(path = "/clients/{clientId}/records", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listClientRecords",
            summary = "Os recordes do aluno vinculado por exercício",
            responses = {
                @ApiResponse(responseCode = "200", description = "Recordes (a lista pode ser vazia)."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo com o aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<MyRecordController.ExerciseRecordsResponse> ofClient(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return MyRecordController.ExerciseRecordsResponse.of(clientRecords.ofClient(jwt.getSubject(), clientId));
    }

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail clientNotFound(ClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Cliente não encontrado");
        return problem;
    }
}
