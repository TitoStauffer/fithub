package com.fithub.training.application;

import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.session.InvalidSetException;
import com.fithub.training.domain.session.SessionClosedException;
import com.fithub.training.domain.session.SessionConflictException;
import com.fithub.training.domain.session.SessionNotFoundException;
import com.fithub.training.domain.session.SessionRepository;
import com.fithub.training.domain.session.SetRepository;
import com.fithub.training.domain.session.WorkoutSession;
import com.fithub.training.domain.session.WorkoutSet;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Registro do treino por série (DEV-0066, FR-48, BR-08, BR-09): cada série fica na sessão, que guarda a versão da ficha;
 * o registro e a correção valem até o fim do dia em que o treino começou.
 */
@Service
public class SessionSets {

    private final SessionRepository sessions;
    private final SetRepository sets;
    private final PlanRepository plans;
    private final ExerciseRepository exercises;
    private final Clock clock;

    public SessionSets(
            SessionRepository sessions,
            SetRepository sets,
            PlanRepository plans,
            ExerciseRepository exercises,
            Clock clock) {
        this.sessions = sessions;
        this.sets = sets;
        this.plans = plans;
        this.exercises = exercises;
        this.clock = clock;
    }

    /** O que o app manda para uma série. */
    public record SetDraft(Integer itemPosition, Integer setNumber, BigDecimal loadKg, Integer reps, UUID exerciseId) {}

    /**
     * O treino como o app precisa para registrar: a divisão na versão da sessão, as séries já feitas e as do último
     * treino com cada exercício, para pré-preencher (FR-48).
     *
     * @param correctable se ainda dá para registrar e corrigir (até o fim do dia do treino)
     * @param lastSets séries do último treino por exercício
     */
    public record SessionView(
            WorkoutSession session,
            PlanVersion version,
            List<WorkoutSet> sets,
            Map<UUID, List<WorkoutSet>> lastSets,
            Map<UUID, Exercise> exercises,
            boolean correctable) {

        public PlanVersion.Division division() {
            return version.divisions().get(session.divisionPosition());
        }
    }

    public SessionView view(String clientAccount, UUID sessionId) {
        WorkoutSession session = owned(clientAccount, sessionId);
        PlanVersion version = versionOf(session);
        Map<UUID, List<WorkoutSet>> lastSets = new HashMap<>();
        Map<UUID, Exercise> found = new HashMap<>();
        for (PlanItem item : version.divisions().get(session.divisionPosition()).items()) {
            for (UUID exerciseId : item.exerciseIds()) {
                lastSets.computeIfAbsent(exerciseId, id -> sets.lastWithExercise(session.clientId(), id, session.id()));
                exercises.findById(exerciseId).ifPresent(exercise -> found.put(exerciseId, exercise));
            }
        }
        return new SessionView(
                session,
                version,
                sets.findBySession(sessionId),
                lastSets,
                found,
                session.correctable(clock.instant(), ManagePlans.ZONE));
    }

    /**
     * Registra a série; o mesmo id corrige em vez de duplicar (toque duplo, nova tentativa).
     *
     * @throws SessionClosedException se o prazo de correção acabou
     */
    public WorkoutSet record(String clientAccount, UUID sessionId, UUID setId, SetDraft draft) {
        WorkoutSession session = correctableSession(clientAccount, sessionId);
        PlanItem item = itemAt(session, draft.itemPosition());
        UUID done = exerciseDone(item, draft.exerciseId());
        Instant now = clock.instant();
        Optional<WorkoutSet> existing = sets.findById(setId);
        if (existing.isPresent() && !existing.get().sessionId().equals(sessionId)) {
            throw new SessionConflictException("Esse id já é de uma série de outro treino.");
        }
        // Corrigir pelo mesmo id só troca carga e repetições: a série continua no mesmo item e número.
        if (existing.isPresent()
                && (existing.get().itemPosition() != draft.itemPosition()
                        || !Integer.valueOf(existing.get().setNumber()).equals(draft.setNumber()))) {
            throw new SessionConflictException("Esse id já é de outra série deste treino (outro exercício ou número).");
        }
        WorkoutSet set = existing.map(found -> found.correct(done, draft.loadKg(), required("reps", draft.reps()), now))
                .orElseGet(() -> new WorkoutSet(
                        setId,
                        sessionId,
                        draft.itemPosition(),
                        required("setNumber", draft.setNumber()),
                        done,
                        draft.loadKg(),
                        required("reps", draft.reps()),
                        now,
                        now));
        boolean saved;
        try {
            saved = sets.save(set);
        } catch (DataIntegrityViolationException sameSlot) {
            throw new SessionConflictException("Essa série já foi registrada com outro id.");
        }
        if (!saved) {
            // O id passou a ser de outra sessão entre a leitura e a gravação (o upsert não toca outra sessão).
            throw new SessionConflictException("A série foi gravada em outro treino ao mesmo tempo; tente de novo.");
        }
        return set;
    }

    /** Apaga a série, no mesmo prazo da correção. */
    public void remove(String clientAccount, UUID sessionId, UUID setId) {
        correctableSession(clientAccount, sessionId);
        WorkoutSet set = sets.findById(setId)
                .filter(found -> found.sessionId().equals(sessionId))
                .orElseThrow(SessionNotFoundException::new);
        sets.delete(set.id());
    }

    private WorkoutSession owned(String clientAccount, UUID sessionId) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        return sessions.findById(sessionId)
                .filter(session -> session.ownedBy(clientId))
                .orElseThrow(SessionNotFoundException::new);
    }

    private WorkoutSession correctableSession(String clientAccount, UUID sessionId) {
        WorkoutSession session = owned(clientAccount, sessionId);
        if (!session.correctable(clock.instant(), ManagePlans.ZONE)) {
            throw new SessionClosedException();
        }
        return session;
    }

    private PlanVersion versionOf(WorkoutSession session) {
        return plans.findVersionById(session.versionId()).orElseThrow(SessionNotFoundException::new);
    }

    /** O item da divisão da sessão, na versão em que ela foi feita. */
    private PlanItem itemAt(WorkoutSession session, Integer itemPosition) {
        List<PlanItem> items =
                versionOf(session).divisions().get(session.divisionPosition()).items();
        if (itemPosition == null || itemPosition < 0 || itemPosition >= items.size()) {
            throw new InvalidSetException("itemPosition", "Esse exercício não está neste treino.");
        }
        return items.get(itemPosition);
    }

    /**
     * O exercício feito: o principal do item, sem informar, ou uma das alternativas previstas (DEV-0067, FR-49).
     */
    private static UUID exerciseDone(PlanItem item, UUID exerciseId) {
        if (exerciseId == null) {
            return item.exerciseId();
        }
        if (!item.exerciseIds().contains(exerciseId)) {
            throw new InvalidSetException("exerciseId", "Use o exercício do treino ou uma alternativa prevista.");
        }
        return exerciseId;
    }

    /** Comentário e sensação do treino, no mesmo prazo da correção das séries (BR-08). */
    public WorkoutSession feedback(String clientAccount, UUID sessionId, String comment, Integer feeling) {
        WorkoutSession session = correctableSession(clientAccount, sessionId).withFeedback(comment, feeling);
        sessions.save(session);
        return session;
    }

    private static int required(String field, Integer value) {
        if (value == null) {
            throw new InvalidSetException(field, "Informe o valor.");
        }
        return value;
    }
}
