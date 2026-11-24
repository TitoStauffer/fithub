package com.fithub.training.application;

import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.plan.InvalidPlanException;
import com.fithub.training.domain.plan.PlanNotFoundException;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.WorkoutPlan;
import com.fithub.training.domain.session.SessionConflictException;
import com.fithub.training.domain.session.SessionNotFoundException;
import com.fithub.training.domain.session.SessionRepository;
import com.fithub.training.domain.session.WorkoutSession;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * O treino do aluno (DEV-0065, FR-29, FR-47): as fichas ativas com o próximo treino da sequência, e a sessão que ele
 * inicia e finaliza. Finalizar avança a sequência.
 */
@Service
public class ClientWorkouts {

    public static final int HISTORY_PAGE_SIZE = 20;

    private final PlanRepository plans;
    private final SessionRepository sessions;
    private final ExerciseRepository exercises;
    private final Clock clock;

    public ClientWorkouts(PlanRepository plans, SessionRepository sessions, ExerciseRepository exercises, Clock clock) {
        this.plans = plans;
        this.sessions = sessions;
        this.exercises = exercises;
        this.clock = clock;
    }

    /**
     * Ficha ativa com o que a tela do aluno precisa.
     *
     * @param nextDivision posição do próximo treino na sequência
     * @param expired se a validade passou: o aluno continua vendo e registrando (PRD §13)
     * @param openSession a sessão aberta nessa ficha, para "continuar treino"
     * @param exercises os exercícios da ficha (principais e alternativas), para a tela mostrar nome e mídia
     */
    public record ActivePlan(
            WorkoutPlan plan,
            int nextDivision,
            boolean expired,
            Optional<WorkoutSession> openSession,
            Map<UUID, Exercise> exercises) {}

    /** As fichas publicadas do aluno, cada uma com o próximo treino. */
    public List<ActivePlan> activePlans(String clientAccount) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        if (clientId == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock.withZone(ManagePlans.ZONE));
        return plans.findPublishedForClient(clientId).stream()
                .map(plan -> new ActivePlan(
                        plan,
                        WorkoutSession.nextDivision(
                                sessions.lastFinished(plan.id()).orElse(null),
                                plan.current().divisions().size()),
                        today.isAfter(plan.expiresOn()),
                        sessions.open(plan.id()),
                        exercisesOf(plan)))
                .toList();
    }

    /**
     * Inicia o próximo treino da ficha. Repetir com o mesmo id devolve a mesma sessão (toque duplo).
     *
     * @throws SessionConflictException se já há outra sessão aberta nessa ficha
     */
    public WorkoutSession start(String clientAccount, UUID sessionId, UUID planId) {
        if (planId == null) {
            throw new InvalidPlanException("planId", "Informe a ficha.");
        }
        UUID clientId = ViewExercise.accountId(clientAccount);
        Optional<WorkoutSession> existing = sessions.findById(sessionId);
        if (existing.isPresent()) {
            if (existing.get().ownedBy(clientId) && existing.get().planId().equals(planId)) {
                return existing.get();
            }
            throw new SessionConflictException("Esse id já é de outra sessão.");
        }
        WorkoutPlan plan = plans.findById(planId)
                .filter(found -> found.clientId().equals(clientId))
                .filter(found -> found.status() == WorkoutPlan.Status.PUBLISHED)
                .orElseThrow(PlanNotFoundException::new);
        if (sessions.open(planId).isPresent()) {
            throw new SessionConflictException("Já há um treino em andamento nesta ficha.");
        }
        WorkoutSession session = new WorkoutSession(
                sessionId,
                clientId,
                planId,
                plan.current().id(),
                WorkoutSession.nextDivision(
                        sessions.lastFinished(planId).orElse(null),
                        plan.current().divisions().size()),
                clock.instant(),
                null);
        try {
            sessions.save(session);
        } catch (DataIntegrityViolationException openElsewhere) {
            // Dois inícios ao mesmo tempo: o segundo bate no índice de uma sessão aberta por ficha.
            throw new SessionConflictException("Já há um treino em andamento nesta ficha.");
        }
        return session;
    }

    /** Finaliza o treino; finalizar de novo não muda nada. */
    public WorkoutSession finish(String clientAccount, UUID sessionId) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        WorkoutSession session = sessions.findById(sessionId)
                .filter(found -> found.ownedBy(clientId))
                .orElseThrow(SessionNotFoundException::new);
        WorkoutSession finished = session.finish(clock.instant());
        if (!session.finished()) {
            sessions.save(finished);
        }
        return finished;
    }

    /** Página do histórico de treinos. */
    public record History(List<SessionRepository.FinishedSummary> items, long total, int page, int size) {}

    /**
     * Os treinos finalizados do aluno, do mais novo para o mais antigo, em páginas de {@value #HISTORY_PAGE_SIZE}
     * (DEV-0072). O histórico é do aluno: continua depois que o vínculo acaba (BR-06).
     */
    public History history(String clientAccount, Integer page) {
        int number = page == null ? 0 : page;
        if (number < 0) {
            throw new InvalidPlanException("page", "A página começa em 0.");
        }
        UUID clientId = ViewExercise.accountId(clientAccount);
        if (clientId == null) {
            return new History(List.of(), 0, number, HISTORY_PAGE_SIZE);
        }
        return new History(
                sessions.finished(clientId, number * HISTORY_PAGE_SIZE, HISTORY_PAGE_SIZE),
                sessions.countFinished(clientId),
                number,
                HISTORY_PAGE_SIZE);
    }

    private Map<UUID, Exercise> exercisesOf(WorkoutPlan plan) {
        Map<UUID, Exercise> found = new HashMap<>();
        for (UUID id : plan.current().exerciseIds()) {
            exercises.findById(id).ifPresent(exercise -> found.put(id, exercise));
        }
        return found;
    }
}
