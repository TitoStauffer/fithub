package com.fithub.training.application;

import com.fithub.training.domain.access.LinkProjection;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.progress.ProgressPeriod;
import com.fithub.training.domain.progress.ProgressRepository;
import com.fithub.training.domain.progress.TrainingAdherence;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Evolução do treino (DEV-0091, FR-56, FR-58, FR-59): aderência, carga e volume por exercício e a última atividade de
 * cada aluno, calculadas na leitura. O personal com vínculo ativo vê as fichas dele (BR-02; a ficha própria do aluno
 * não entra, QUESTÃO-03); o aluno vê todas as dele. A nutricionista não tem rota (BR-03).
 */
@Service
public class TrainingProgress {

    /** O "hoje" e os dias são os do Brasil, não os do servidor. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ProgressRepository progress;
    private final ExerciseRepository exercises;
    private final LinkProjection links;
    private final ClientAccess clientAccess;
    private final Clock clock;

    public TrainingProgress(
            ProgressRepository progress,
            ExerciseRepository exercises,
            LinkProjection links,
            ClientAccess clientAccess,
            Clock clock) {
        this.progress = progress;
        this.exercises = exercises;
        this.links = links;
        this.clientAccess = clientAccess;
        this.clock = clock;
    }

    /** Um ponto da evolução de um exercício: a data do treino, a maior carga e o volume. */
    public record Point(LocalDate date, BigDecimal maxLoadKg, BigDecimal volumeKg) {}

    /** A evolução de um exercício no período, com o exercício para a tela mostrar o nome. */
    public record ExerciseProgress(UUID exerciseId, Exercise exercise, List<Point> points) {}

    /** A evolução do período. */
    public record View(ProgressPeriod period, TrainingAdherence.Result adherence, List<ExerciseProgress> exercises) {}

    /** A última atividade de um aluno vinculado; vazia se ele nunca finalizou um treino nas fichas do personal. */
    public record Activity(UUID clientId, Instant lastActivityAt) {}

    /** A evolução do aluno vinculado, nas fichas do personal. Sem vínculo ativo, 404. */
    public View ofClient(String personalAccount, UUID clientId, LocalDate from, LocalDate to) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        return view(clientId, ViewExercise.accountId(personalAccount), from, to);
    }

    /** A evolução do próprio aluno, em todas as fichas dele (inclusive a própria). */
    public View mine(String clientAccount, LocalDate from, LocalDate to) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        if (clientId == null) {
            ProgressPeriod period = ProgressPeriod.of(from, to, today());
            return new View(period, TrainingAdherence.of(period, List.of(), List.of()), List.of());
        }
        return view(clientId, null, from, to);
    }

    /** A última atividade de cada aluno com vínculo ativo, nas fichas do personal (base da lista de inativos). */
    public List<Activity> activity(String personalAccount) {
        UUID personalId = ViewExercise.accountId(personalAccount);
        if (personalId == null) {
            return List.of();
        }
        List<UUID> clients = links.activeClients(personalId);
        Map<UUID, Instant> last = progress.lastFinished(personalId, clients);
        return clients.stream()
                .map(client -> new Activity(client, last.get(client)))
                .toList();
    }

    private View view(UUID clientId, UUID personalId, LocalDate from, LocalDate to) {
        ProgressPeriod period = ProgressPeriod.of(from, to, today());
        Instant start = period.from().atStartOfDay(ZONE).toInstant();
        Instant end = period.to().plusDays(1).atStartOfDay(ZONE).toInstant();
        List<TrainingAdherence.PlanWindow> windows = progress.publishedPlans(clientId, personalId).stream()
                .map(plan -> new TrainingAdherence.PlanWindow(
                        plan.planId(),
                        day(plan.firstPublishedAt()),
                        plan.replacedAt() == null ? null : day(plan.replacedAt()),
                        plan.divisions()))
                .toList();
        List<LocalDate> finished = progress.finishedSessions(clientId, personalId, start, end).stream()
                .map(TrainingProgress::day)
                .toList();
        return new View(
                period,
                TrainingAdherence.of(period, windows, finished),
                exercises(progress.exerciseSessions(clientId, personalId, start, end)));
    }

    /** Os pontos de cada exercício, na ordem em que foram treinados pela primeira vez no período. */
    private List<ExerciseProgress> exercises(List<ProgressRepository.ExerciseSession> sessions) {
        Map<UUID, List<Point>> byExercise = new LinkedHashMap<>();
        for (ProgressRepository.ExerciseSession session : sessions) {
            byExercise
                    .computeIfAbsent(session.exerciseId(), id -> new ArrayList<>())
                    .add(new Point(day(session.finishedAt()), session.maxLoadKg(), session.volumeKg()));
        }
        Map<UUID, Optional<Exercise>> cache = new HashMap<>();
        return byExercise.entrySet().stream()
                .map(entry -> new ExerciseProgress(
                        entry.getKey(),
                        cache.computeIfAbsent(entry.getKey(), exercises::findById)
                                .orElse(null),
                        entry.getValue()))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
