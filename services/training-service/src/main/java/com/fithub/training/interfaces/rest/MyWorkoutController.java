package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.ClientWorkouts;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.plan.InvalidPlanException;
import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanNotFoundException;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
import com.fithub.training.domain.session.SessionConflictException;
import com.fithub.training.domain.session.SessionNotFoundException;
import com.fithub.training.domain.session.SessionRepository;
import com.fithub.training.domain.session.WorkoutSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O treino do aluno (DEV-0065, FR-29, FR-47): as fichas ativas com o próximo treino e os exercícios embutidos, e a
 * sessão que ele inicia e finaliza.
 */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Treino do aluno", description = "Fichas ativas, próximo treino e sessões do aluno")
class MyWorkoutController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ClientWorkouts clientWorkouts;

    MyWorkoutController(ClientWorkouts clientWorkouts) {
        this.clientWorkouts = clientWorkouts;
    }

    @Schema(name = "ClientExercise", description = "O exercício como o aluno vê: nome, mídia e crédito.")
    record ExerciseSummary(
            UUID id,
            String name,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Vídeo ou imagem, https; ausente se não houver.")
            String mediaUrl,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Presente só em exercício importado; a tela mostra o crédito.")
            ExerciseController.AttributionResponse attribution) {

        static ExerciseSummary of(UUID id, Map<UUID, Exercise> exercises) {
            Exercise exercise = exercises.get(id);
            if (exercise == null) {
                return new ExerciseSummary(id, "Exercício indisponível", null, null);
            }
            return new ExerciseSummary(
                    id,
                    exercise.name(),
                    exercise.mediaUrl(),
                    ExerciseController.AttributionResponse.of(exercise.attribution()));
        }
    }

    @Schema(name = "ClientWorkoutItem", description = "Exercício prescrito, com o exercício e as alternativas.")
    record ItemResponse(
            ExerciseSummary exercise,
            int sets,

            @Schema(description = "Igual ao máximo quando as repetições são fixas.")
            int repsMin,

            int repsMax,
            @Schema(nullable = true) BigDecimal loadKg,
            @Schema(nullable = true) Integer restSeconds,
            @Schema(nullable = true) String note,
            @Schema(nullable = true) PlanItem.Technique technique,
            List<ExerciseSummary> alternatives) {

        static ItemResponse of(PlanItem item, Map<UUID, Exercise> exercises) {
            return new ItemResponse(
                    ExerciseSummary.of(item.exerciseId(), exercises),
                    item.sets(),
                    item.repsMin(),
                    item.repsMax(),
                    item.loadKg(),
                    item.restSeconds(),
                    item.note(),
                    item.technique(),
                    item.alternatives().stream()
                            .map(id -> ExerciseSummary.of(id, exercises))
                            .toList());
        }
    }

    @Schema(name = "ClientWorkoutDivision")
    record DivisionResponse(
            int position,
            String letter,
            @Schema(nullable = true) String name,
            List<ItemResponse> items) {}

    @Schema(name = "ActivePlan", description = "Ficha publicada do aluno com o próximo treino da sequência.")
    record ActivePlanResponse(
            UUID planId,
            int version,
            String name,
            LocalDate startsOn,
            LocalDate expiresOn,

            @Schema(description = "A validade passou; o aluno continua vendo e registrando.")
            boolean expired,

            @Schema(description = "Posição do próximo treino, a partir de 0.")
            int nextDivision,

            String nextDivisionLetter,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Sessão aberta nessa ficha, para continuar o treino; ausente se não houver.")
            UUID openSessionId,

            List<DivisionResponse> divisions) {

        static ActivePlanResponse of(ClientWorkouts.ActivePlan active) {
            WorkoutPlan plan = active.plan();
            PlanVersion version = plan.current();
            List<PlanVersion.Division> divisions = version.divisions();
            return new ActivePlanResponse(
                    plan.id(),
                    version.number(),
                    version.name(),
                    version.startsOn(),
                    version.expiresOn(),
                    active.expired(),
                    active.nextDivision(),
                    divisions.get(active.nextDivision()).letter(),
                    active.openSession().map(WorkoutSession::id).orElse(null),
                    IntStream.range(0, divisions.size())
                            .mapToObj(position -> new DivisionResponse(
                                    position,
                                    divisions.get(position).letter(),
                                    divisions.get(position).name(),
                                    divisions.get(position).items().stream()
                                            .map(item -> ItemResponse.of(item, active.exercises()))
                                            .toList()))
                            .toList());
        }
    }

    @Schema(name = "WorkoutSessionInput")
    record SessionRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID planId) {}

    @Schema(name = "WorkoutSession", description = "Sessão de treino: a divisão da ficha, na versão que estava ativa.")
    record SessionResponse(
            UUID id,
            UUID planId,
            int divisionPosition,
            Instant startedAt,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Quando o aluno finalizou; ausente enquanto está aberta.")
            Instant finishedAt) {

        static SessionResponse of(WorkoutSession session) {
            return new SessionResponse(
                    session.id(),
                    session.planId(),
                    session.divisionPosition(),
                    session.startedAt(),
                    session.finishedAt());
        }
    }

    @GetMapping(path = "/me/plans/active", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "As fichas publicadas do aluno, com o próximo treino da sequência",
            responses = {
                @ApiResponse(responseCode = "200", description = "Fichas ativas (a lista pode ser vazia)."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<ActivePlanResponse> activePlans(@AuthenticationPrincipal Jwt jwt) {
        return clientWorkouts.activePlans(jwt.getSubject()).stream()
                .map(ActivePlanResponse::of)
                .toList();
    }

    @Schema(name = "SessionSummary", description = "Treino feito, no histórico.")
    record SessionSummaryResponse(
            UUID id,
            UUID planId,

            @Schema(description = "O nome da ficha na versão em que o treino foi feito.")
            String planName,

            int divisionPosition,
            String divisionLetter,
            @JsonInclude(JsonInclude.Include.NON_NULL) String divisionName,
            Instant startedAt,
            Instant finishedAt,

            @Schema(description = "Quantas séries foram registradas.")
            int setCount,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "De 1 a 5; ausente sem sensação.")
            Integer feeling) {

        static SessionSummaryResponse of(SessionRepository.FinishedSummary summary) {
            return new SessionSummaryResponse(
                    summary.id(),
                    summary.planId(),
                    summary.planName(),
                    summary.divisionPosition(),
                    summary.divisionLetter(),
                    summary.divisionName(),
                    summary.startedAt(),
                    summary.finishedAt(),
                    summary.setCount(),
                    summary.feeling());
        }
    }

    @Schema(name = "SessionHistoryPage", description = "Página do histórico de treinos.")
    record HistoryResponse(
            List<SessionSummaryResponse> items,

            @Schema(description = "Quantos treinos finalizados o aluno tem, somando todas as páginas.")
            long total,

            int page,
            int size) {}

    @GetMapping(path = "/me/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listMySessions",
            summary = "Histórico: os treinos finalizados do aluno, do mais novo para o mais antigo",
            description = "Páginas de 20, a partir de 0. Continua depois que o vínculo acaba (BR-06).",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página do histórico."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Página negativa.",
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
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    HistoryResponse history(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Página, a partir de 0.", schema = @Schema(defaultValue = "0"))
                    @RequestParam(required = false)
                    Integer page) {
        ClientWorkouts.History history = clientWorkouts.history(jwt.getSubject(), page);
        return new HistoryResponse(
                history.items().stream().map(SessionSummaryResponse::of).toList(),
                history.total(),
                history.page(),
                history.size());
    }

    @PutMapping(
            path = "/me/sessions/{sessionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Inicia o próximo treino da ficha",
            description = "O id é gerado pelo app: repetir com o mesmo id devolve a mesma sessão.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Sessão iniciada (ou a mesma, se já existia)."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Ficha não informada.",
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
                        description = "A ficha não existe, não é do aluno ou não está publicada.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Já há um treino em andamento nesta ficha.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    SessionResponse start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId, @RequestBody SessionRequest request) {
        return SessionResponse.of(clientWorkouts.start(jwt.getSubject(), sessionId, request.planId()));
    }

    @PostMapping(path = "/me/sessions/{sessionId}/finish", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Finaliza o treino; a sequência avança",
            responses = {
                @ApiResponse(responseCode = "200", description = "Sessão finalizada."),
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
    SessionResponse finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return SessionResponse.of(clientWorkouts.finish(jwt.getSubject(), sessionId));
    }

    @ExceptionHandler(InvalidPlanException.class)
    ProblemDetail invalid(InvalidPlanException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler({PlanNotFoundException.class, SessionNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Não encontrado");
        return problem;
    }

    @ExceptionHandler(SessionConflictException.class)
    ProblemDetail conflict(SessionConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Treino em andamento");
        return problem;
    }
}
