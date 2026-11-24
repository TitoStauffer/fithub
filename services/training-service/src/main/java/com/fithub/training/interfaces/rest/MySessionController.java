package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.SessionSets;
import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.session.InvalidSetException;
import com.fithub.training.domain.session.SessionClosedException;
import com.fithub.training.domain.session.SessionConflictException;
import com.fithub.training.domain.session.SessionNotFoundException;
import com.fithub.training.domain.session.WorkoutSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro do treino por série (DEV-0066, FR-48): o treino com o prescrito e o último treino para pré-preencher, e a
 * série registrada ou corrigida pelo id do app, até o fim do dia do treino (BR-08).
 */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Treino do aluno", description = "Fichas ativas, próximo treino e sessões do aluno")
class MySessionController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final SessionSets sessionSets;

    MySessionController(SessionSets sessionSets) {
        this.sessionSets = sessionSets;
    }

    @Schema(name = "RecordedSet", description = "Série registrada.")
    record SetResponse(UUID id, int setNumber, UUID exerciseId, BigDecimal loadKg, int reps) {

        static SetResponse of(WorkoutSet set) {
            return new SetResponse(set.id(), set.setNumber(), set.exerciseId(), set.loadKg(), set.reps());
        }
    }

    @Schema(name = "SessionItem", description = "Exercício do treino com o prescrito, o feito e o último treino.")
    record SessionItemResponse(
            int position,
            MyWorkoutController.ExerciseSummary exercise,
            int sets,
            int repsMin,
            int repsMax,
            @Schema(nullable = true) BigDecimal loadKg,
            @Schema(nullable = true) Integer restSeconds,
            @Schema(nullable = true) String note,
            @Schema(nullable = true) PlanItem.Technique technique,
            List<MyWorkoutController.ExerciseSummary> alternatives,

            @Schema(description = "Séries já registradas neste treino.")
            List<SetResponse> recorded,

            @Schema(description = "Séries do último treino com o exercício, para pré-preencher.")
            List<SetResponse> last) {}

    @Schema(name = "SessionView", description = "O treino em andamento (ou já feito), na versão da ficha da sessão.")
    record SessionViewResponse(
            UUID id,
            UUID planId,
            int versionNumber,
            int divisionPosition,
            String divisionLetter,
            @Schema(nullable = true) String divisionName,
            Instant startedAt,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Quando o aluno finalizou; ausente enquanto está aberta.")
            Instant finishedAt,

            @Schema(description = "Se ainda dá para registrar e corrigir: até o fim do dia do treino.")
            boolean correctable,

            List<SessionItemResponse> items) {

        static SessionViewResponse of(SessionSets.SessionView view) {
            PlanVersion.Division division = view.division();
            List<PlanItem> items = division.items();
            return new SessionViewResponse(
                    view.session().id(),
                    view.session().planId(),
                    view.version().number(),
                    view.session().divisionPosition(),
                    division.letter(),
                    division.name(),
                    view.session().startedAt(),
                    view.session().finishedAt(),
                    view.correctable(),
                    IntStream.range(0, items.size())
                            .mapToObj(position -> item(view, position, items.get(position)))
                            .toList());
        }

        private static SessionItemResponse item(SessionSets.SessionView view, int position, PlanItem item) {
            return new SessionItemResponse(
                    position,
                    MyWorkoutController.ExerciseSummary.of(item.exerciseId(), view.exercises()),
                    item.sets(),
                    item.repsMin(),
                    item.repsMax(),
                    item.loadKg(),
                    item.restSeconds(),
                    item.note(),
                    item.technique(),
                    item.alternatives().stream()
                            .map(id -> MyWorkoutController.ExerciseSummary.of(id, view.exercises()))
                            .toList(),
                    view.sets().stream()
                            .filter(set -> set.itemPosition() == position)
                            .map(SetResponse::of)
                            .toList(),
                    view.lastSets().getOrDefault(item.exerciseId(), List.of()).stream()
                            .map(SetResponse::of)
                            .toList());
        }
    }

    @Schema(name = "RecordedSetInput", description = "Carga e repetições reais de uma série.")
    record SetRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Posição do exercício na divisão.")
            Integer itemPosition,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Número da série, de 1 a 20.")
            Integer setNumber,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 0 a 1000 kg, em passos de 0,25.")
            BigDecimal loadKg,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 0 a 300.")
            Integer reps) {}

    @GetMapping(path = "/me/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "viewSession",
            summary = "O treino com o prescrito, as séries feitas e o último treino de cada exercício",
            responses = {
                @ApiResponse(responseCode = "200", description = "Treino."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A sessão não existe ou é de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    SessionViewResponse view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return SessionViewResponse.of(sessionSets.view(jwt.getSubject(), sessionId));
    }

    @PutMapping(
            path = "/me/sessions/{sessionId}/sets/{setId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "recordSet",
            summary = "Registra ou corrige uma série",
            description = "O id é gerado pelo app: repetir corrige a mesma série em vez de criar outra.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Série registrada."),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Campo fora das regras ou exercício que não está no treino; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A sessão não existe ou é de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O prazo de correção acabou, ou a série já foi registrada com outro id.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    SetResponse record(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @PathVariable UUID setId,
            @RequestBody SetRequest request) {
        return SetResponse.of(sessionSets.record(
                jwt.getSubject(),
                sessionId,
                setId,
                new SessionSets.SetDraft(
                        request.itemPosition(), request.setNumber(), request.loadKg(), request.reps())));
    }

    @DeleteMapping(path = "/me/sessions/{sessionId}/sets/{setId}")
    @Operation(
            operationId = "removeSet",
            summary = "Apaga uma série, no mesmo prazo da correção",
            responses = {
                @ApiResponse(responseCode = "204", description = "Série apagada.", content = @Content()),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A sessão ou a série não existe, ou é de outro aluno.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "O prazo de correção acabou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId, @PathVariable UUID setId) {
        sessionSets.remove(jwt.getSubject(), sessionId, setId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidSetException.class)
    ProblemDetail invalid(InvalidSetException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail notFound(SessionNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }

    @ExceptionHandler({SessionClosedException.class, SessionConflictException.class})
    ProblemDetail conflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Registro fora do prazo ou repetido");
        return problem;
    }
}
