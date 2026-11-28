package com.fithub.training.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fithub.training.application.ManagePlans;
import com.fithub.training.application.PlanAlerts;
import com.fithub.training.domain.access.ClientNotFoundException;
import com.fithub.training.domain.access.SubscriptionRequiredException;
import com.fithub.training.domain.plan.InvalidPlanException;
import com.fithub.training.domain.plan.PlanConflictException;
import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanNotFoundException;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.PlanStateException;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
import com.fithub.training.domain.restriction.BodyRegion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
 * Fichas de treino do personal (DEV-0059, FR-28, FR-32, FR-34): criar em rascunho, editar, publicar e ver as que
 * vencem. Sem vínculo ativo, criar, editar e publicar respondem 404; a ficha de outro personal também.
 */
@RestController
@PreAuthorize("hasRole('PERSONAL')")
@Tag(name = "Fichas de treino", description = "Fichas de treino que o personal monta para o aluno")
class PlanController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManagePlans managePlans;
    private final PlanAlerts planAlerts;

    PlanController(ManagePlans managePlans, PlanAlerts planAlerts) {
        this.managePlans = managePlans;
        this.planAlerts = planAlerts;
    }

    /** A ficha com os alertas de restrição calculados agora (DEV-0062). */
    private PlanResponse respond(WorkoutPlan plan) {
        return PlanResponse.of(plan, planAlerts.of(plan));
    }

    @Schema(
            name = "WorkoutPlanAlert",
            description = "Exercício da ficha que envolve uma região restrita do aluno. Alerta, não bloqueio (BR-11).")
    record AlertResponse(
            @Schema(description = "Posição da divisão, a partir de 0.")
            int divisionPosition,

            String divisionLetter,

            @Schema(description = "Posição do item na divisão, a partir de 0.")
            int itemPosition,

            UUID exerciseId,

            @Schema(description = "Se o exercício é uma alternativa do item.")
            boolean alternative,

            BodyRegion region,
            UUID restrictionId,

            @Schema(description = "Descrição da restrição, como o personal registrou.")
            String description) {

        static AlertResponse of(PlanAlerts.Alert alert) {
            return new AlertResponse(
                    alert.divisionPosition(),
                    alert.divisionLetter(),
                    alert.itemPosition(),
                    alert.exerciseId(),
                    alert.alternative(),
                    alert.region(),
                    alert.restrictionId(),
                    alert.description());
        }
    }

    @Schema(name = "WorkoutDivision", description = "Divisão da ficha, na ordem da sequência.")
    record DivisionResponse(
            @Schema(description = "Letra da posição: A, B, C...")
            String letter,

            @Schema(nullable = true) String name,

            @Schema(description = "Exercícios prescritos, na ordem.")
            List<ItemResponse> items) {}

    @Schema(name = "WorkoutItem", description = "Exercício prescrito na divisão.")
    record ItemResponse(
            UUID exerciseId,
            int sets,

            @Schema(description = "Igual ao máximo quando as repetições são fixas.")
            int repsMin,

            int repsMax,

            @Schema(nullable = true, description = "Carga sugerida em kg.")
            BigDecimal loadKg,

            @Schema(nullable = true, description = "Descanso entre as séries, em segundos.")
            Integer restSeconds,

            @Schema(nullable = true) String note,
            @Schema(nullable = true) PlanItem.Technique technique,

            @Schema(description = "Exercícios alternativos, na ordem.")
            List<UUID> alternatives) {

        static ItemResponse of(PlanItem item) {
            return new ItemResponse(
                    item.exerciseId(),
                    item.sets(),
                    item.repsMin(),
                    item.repsMax(),
                    item.loadKg(),
                    item.restSeconds(),
                    item.note(),
                    item.technique(),
                    item.alternatives());
        }
    }

    @Schema(name = "WorkoutItemInput", description = "Exercício prescrito, com as regras do modelo.")
    record ItemRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID exerciseId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 1 a 20.")
            Integer sets,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Repetições, de 1 a 100.")
            Integer repsMin,

            @Schema(nullable = true, description = "Máximo da faixa (8–12); vazio para repetições fixas.")
            Integer repsMax,

            @Schema(nullable = true, description = "De 0 a 1000 kg, em passos de 0,5.")
            BigDecimal loadKg,

            @Schema(nullable = true, description = "De 0 a 600 segundos.")
            Integer restSeconds,

            @Schema(nullable = true, description = "Até 500 caracteres.")
            String note,

            @Schema(nullable = true, implementation = PlanItem.Technique.class)
            String technique,

            @Schema(nullable = true, description = "Até 3 exercícios, diferentes do principal.")
            List<UUID> alternatives) {

        PlanItem.Draft toDraft() {
            return new PlanItem.Draft(
                    exerciseId, sets, repsMin, repsMax, loadKg, restSeconds, note, technique, alternatives);
        }
    }

    @Schema(name = "WorkoutPlan", description = "Ficha de treino, com o conteúdo da versão atual.")
    record PlanResponse(
            UUID id,
            UUID clientId,
            WorkoutPlan.Status status,
            int version,
            String name,
            LocalDate startsOn,
            int validityWeeks,
            @Schema(description = "Início mais a validade.") LocalDate expiresOn,
            List<DivisionResponse> divisions,
            // Ausente enquanto a ficha é rascunho.
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Quando a versão atual foi publicada; ausente no rascunho.")
            Instant publishedAt,

            Instant createdAt,
            Instant updatedAt,

            @Schema(description = "Alertas de restrição de mobilidade; vazio sem vínculo ativo ou sem restrição.")
            List<AlertResponse> alerts) {

        static PlanResponse of(WorkoutPlan plan, List<PlanAlerts.Alert> alerts) {
            var version = plan.current();
            return new PlanResponse(
                    plan.id(),
                    plan.clientId(),
                    plan.status(),
                    version.number(),
                    version.name(),
                    version.startsOn(),
                    version.validityWeeks(),
                    version.expiresOn(),
                    divisionsOf(version),
                    version.publishedAt(),
                    plan.createdAt(),
                    plan.updatedAt(),
                    alerts.stream().map(AlertResponse::of).toList());
        }
    }

    @Schema(name = "WorkoutDivisionInput")
    record DivisionRequest(
            @Schema(nullable = true, description = "Até 60 caracteres.")
            String name,

            @Schema(nullable = true, description = "Até 30 exercícios, na ordem.")
            List<ItemRequest> items) {

        PlanVersion.DivisionDraft toDraft() {
            return new PlanVersion.DivisionDraft(
                    name,
                    items == null
                            ? null
                            : items.stream()
                                    .map(item -> item == null ? null : item.toDraft())
                                    .toList());
        }
    }

    static List<DivisionResponse> divisionsOf(PlanVersion version) {
        return version.divisions().stream()
                .map(division -> new DivisionResponse(
                        division.letter(),
                        division.name(),
                        division.items().stream().map(ItemResponse::of).toList()))
                .toList();
    }

    @Schema(name = "WorkoutPlanVersionSummary", description = "Uma versão da ficha, sem o conteúdo.")
    record VersionSummaryResponse(
            int number,
            String name,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Quando foi publicada; ausente no rascunho.")
            Instant publishedAt) {

        static VersionSummaryResponse of(PlanRepository.VersionSummary version) {
            return new VersionSummaryResponse(version.number(), version.name(), version.publishedAt());
        }
    }

    @Schema(name = "WorkoutPlanVersion", description = "O conteúdo de uma versão da ficha; a publicada nunca muda.")
    record VersionResponse(
            int number,
            String name,
            LocalDate startsOn,
            int validityWeeks,
            LocalDate expiresOn,
            List<DivisionResponse> divisions,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Quando foi publicada; ausente no rascunho.")
            Instant publishedAt) {

        static VersionResponse of(PlanVersion version) {
            return new VersionResponse(
                    version.number(),
                    version.name(),
                    version.startsOn(),
                    version.validityWeeks(),
                    version.expiresOn(),
                    divisionsOf(version),
                    version.publishedAt());
        }
    }

    @Schema(
            name = "WorkoutPlanInput",
            description = "Cabeçalho e divisões da ficha. Na ficha publicada, a edição gera a versão seguinte.")
    record PlanRequest(
            @Schema(
                    nullable = true,
                    description = "Versão em que a edição se baseou; obrigatória ao editar a ficha publicada.")
            Integer baseVersion,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 2 a 80 caracteres.")
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate startsOn,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 1 a 52 semanas.")
            Integer validityWeeks,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "De 1 a 7 divisões, na ordem.")
            List<DivisionRequest> divisions) {

        ManagePlans.Draft toDraft() {
            return new ManagePlans.Draft(
                    baseVersion,
                    name,
                    startsOn,
                    validityWeeks,
                    divisions == null
                            ? null
                            : divisions.stream()
                                    .map(division -> division == null ? null : division.toDraft())
                                    .toList());
        }
    }

    @Schema(name = "ExpiringPlan", description = "Ficha publicada que vence no período ou já venceu.")
    record ExpiringResponse(
            UUID planId,
            UUID clientId,

            @Schema(nullable = true, description = "Nome do aluno, quando já chegou à projeção local.")
            String clientName,

            String name,
            LocalDate expiresOn) {

        static ExpiringResponse of(PlanRepository.ExpiringPlan plan) {
            return new ExpiringResponse(
                    plan.planId(), plan.clientId(), plan.clientName(), plan.name(), plan.expiresOn());
        }
    }

    @PostMapping(
            path = "/clients/{clientId}/plans",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cria uma ficha em rascunho para o aluno vinculado",
            responses = {
                @ApiResponse(responseCode = "201", description = "Ficha criada; o endereço vem em Location."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal, ou a assinatura venceu: sem assinatura válida, nada se cria, edita ou publica (BR-14, título \"Assinatura vencida\").",
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
    ResponseEntity<PlanResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @RequestBody PlanRequest request) {
        WorkoutPlan plan = managePlans.create(jwt.getSubject(), clientId, request.toDraft());
        return ResponseEntity.created(URI.create("/plans/" + plan.id())).body(respond(plan));
    }

    @GetMapping(path = "/clients/{clientId}/plans", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Fichas que o personal criou para o aluno vinculado, da mais nova para a mais antiga",
            responses = {
                @ApiResponse(responseCode = "200", description = "Fichas."),
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
    List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return managePlans.listForClient(jwt.getSubject(), clientId).stream()
                .map(this::respond)
                .toList();
    }

    @GetMapping(path = "/plans/expiring", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Fichas publicadas que vencem no período ou já venceram",
            description = "Só as do próprio personal e de alunos com vínculo ativo, pela data de vencimento.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Fichas vencendo."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Período fora de 0 a 60 dias.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<ExpiringResponse> expiring(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Dias à frente, de 0 a 60.", schema = @Schema(defaultValue = "7"))
                    @RequestParam(required = false)
                    Integer withinDays) {
        return managePlans.expiring(jwt.getSubject(), withinDays).stream()
                .map(ExpiringResponse::of)
                .toList();
    }

    @GetMapping(path = "/plans/{planId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Uma ficha do personal",
            description = "O autor lê a ficha mesmo depois que o vínculo acaba (BR-05).",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe ou é de outro personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanResponse view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return respond(managePlans.view(jwt.getSubject(), planId));
    }

    @PutMapping(
            path = "/plans/{planId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Edita a ficha: o rascunho no lugar; a publicada vira a versão seguinte",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha editada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Campo fora das regras; o campo vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal, ou a assinatura venceu: sem assinatura válida, nada se cria, edita ou publica (BR-14, título \"Assinatura vencida\").",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe, é de outro personal ou o vínculo acabou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "A ficha foi substituída, ou outra edição chegou antes (versão base desatualizada).",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @RequestBody PlanRequest request) {
        return respond(managePlans.update(jwt.getSubject(), planId, request.toDraft()));
    }

    @PostMapping(path = "/plans/{planId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Publica a ficha; a que estava ativa para o aluno fica substituída",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha publicada."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Alguma divisão está sem exercício (BR-10); a divisão vem em errors.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal, ou a assinatura venceu: sem assinatura válida, nada se cria, edita ou publica (BR-14, título \"Assinatura vencida\").",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe, é de outro personal ou o vínculo acabou.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "A ficha já foi publicada.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    PlanResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return respond(managePlans.publish(jwt.getSubject(), planId));
    }

    @ExceptionHandler(InvalidPlanException.class)
    ProblemDetail invalid(InvalidPlanException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(SubscriptionRequiredException.class)
    ProblemDetail subscriptionRequired(SubscriptionRequiredException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Assinatura vencida");
        return problem;
    }

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail clientNotFound(ClientNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Cliente não encontrado");
        return problem;
    }

    @ExceptionHandler(PlanNotFoundException.class)
    ProblemDetail planNotFound(PlanNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Ficha não encontrada");
        return problem;
    }

    @GetMapping(path = "/plans/{planId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "As versões da ficha, da primeira para a última",
            responses = {
                @ApiResponse(responseCode = "200", description = "Versões."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha não existe ou é de outro personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<VersionSummaryResponse> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return managePlans.versions(jwt.getSubject(), planId).stream()
                .map(VersionSummaryResponse::of)
                .toList();
    }

    @GetMapping(path = "/plans/{planId}/versions/{number}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Uma versão da ficha, pelo número",
            responses = {
                @ApiResponse(responseCode = "200", description = "Versão."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "A ficha ou a versão não existe, ou a ficha é de outro personal.",
                        content =
                                @Content(
                                        mediaType = PROBLEM,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    VersionResponse version(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable int number) {
        return VersionResponse.of(managePlans.version(jwt.getSubject(), planId, number));
    }

    @ExceptionHandler(PlanConflictException.class)
    ProblemDetail conflict(PlanConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Edição concorrente");
        return problem;
    }

    @ExceptionHandler(PlanStateException.class)
    ProblemDetail state(PlanStateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Operação fora do estado da ficha");
        return problem;
    }
}
