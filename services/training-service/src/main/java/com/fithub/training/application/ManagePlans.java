package com.fithub.training.application;

import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.plan.InvalidPlanException;
import com.fithub.training.domain.plan.PlanConflictException;
import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanNotFoundException;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fichas de treino do personal (DEV-0059, FR-28, FR-32, FR-34). Criar, editar e publicar exigem vínculo ativo com o
 * aluno (BR-02); o autor continua lendo as fichas que criou depois que o vínculo acaba (BR-05). Outro personal nunca
 * vê a ficha.
 */
@Service
public class ManagePlans {

    /** O "hoje" das fichas vencendo é o do Brasil, não o do servidor. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public static final int DEFAULT_EXPIRING_DAYS = 7;
    public static final int MAX_EXPIRING_DAYS = 60;

    private final ClientAccess clientAccess;
    private final PlanRepository plans;
    private final ExerciseRepository exercises;
    private final Clock clock;

    public ManagePlans(ClientAccess clientAccess, PlanRepository plans, ExerciseRepository exercises, Clock clock) {
        this.clientAccess = clientAccess;
        this.plans = plans;
        this.exercises = exercises;
        this.clock = clock;
    }

    /** Cabeçalho e divisões que o personal informa para criar ou editar o rascunho. */
    public record Draft(
            Integer baseVersion,
            String name,
            LocalDate startsOn,
            Integer validityWeeks,
            List<PlanVersion.DivisionDraft> divisions) {

        int weeks() {
            if (validityWeeks == null) {
                throw new InvalidPlanException("validityWeeks", "Informe a validade em semanas.");
            }
            return validityWeeks;
        }
    }

    public WorkoutPlan create(String personalAccount, UUID clientId, Draft draft) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        WorkoutPlan plan = WorkoutPlan.newDraft(
                clientId,
                UUID.fromString(personalAccount),
                draft.name(),
                draft.startsOn(),
                draft.weeks(),
                draft.divisions(),
                clock.instant());
        requireUsableExercises(plan, Set.of());
        plans.save(plan);
        return plan;
    }

    /** As fichas que o personal criou para o aluno vinculado. */
    public List<WorkoutPlan> listForClient(String personalAccount, UUID clientId) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        return plans.findByClientAndPersonal(clientId, UUID.fromString(personalAccount));
    }

    /** A ficha, só para o autor; com ou sem vínculo ativo (BR-05). */
    public WorkoutPlan view(String personalAccount, UUID planId) {
        return authored(personalAccount, planId);
    }

    /**
     * Edita a ficha: o rascunho no lugar; a publicada vira a versão seguinte (DEV-0061, BR-09).
     *
     * @throws PlanConflictException se outra edição chegou antes
     */
    public WorkoutPlan update(String personalAccount, UUID planId, Draft draft) {
        WorkoutPlan plan = authored(personalAccount, planId);
        clientAccess.requireActiveLink(personalAccount, plan.clientId());
        WorkoutPlan edited = plan.edit(
                draft.baseVersion(), draft.name(), draft.startsOn(), draft.weeks(), draft.divisions(), clock.instant());
        requireUsableExercises(edited, plan.current().exerciseIds());
        try {
            plans.save(edited);
        } catch (DataIntegrityViolationException sameVersion) {
            // Duas edições da mesma base ao mesmo tempo: a segunda bate na unicidade (ficha, número).
            throw new PlanConflictException();
        }
        return edited;
    }

    /** As versões da ficha, da primeira para a última, para o autor. */
    public List<PlanRepository.VersionSummary> versions(String personalAccount, UUID planId) {
        return plans.findVersions(authored(personalAccount, planId).id());
    }

    /**
     * As versões publicadas da ficha que o aluno recebeu (DEV-0061): o rascunho nunca aparece para ele.
     *
     * @param clientAccount o {@code sub} do token, que é o id do aluno (ADR-009)
     */
    public List<PlanRepository.VersionSummary> clientVersions(String clientAccount, UUID planId) {
        return plans.findVersions(received(clientAccount, planId).id()).stream()
                .filter(version -> version.publishedAt() != null)
                .toList();
    }

    /** Uma versão publicada da ficha que o aluno recebeu. */
    public PlanVersion clientVersion(String clientAccount, UUID planId, int number) {
        return plans.findVersion(received(clientAccount, planId).id(), number)
                .filter(PlanVersion::published)
                .orElseThrow(PlanNotFoundException::new);
    }

    /** A ficha do aluno, já publicada alguma vez; a de outro aluno e o rascunho respondem como inexistentes. */
    private WorkoutPlan received(String clientAccount, UUID planId) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        return plans.findById(planId)
                .filter(plan -> plan.clientId().equals(clientId))
                .filter(plan -> plan.status() != WorkoutPlan.Status.DRAFT)
                .orElseThrow(PlanNotFoundException::new);
    }

    /** Uma versão da ficha, para o autor. */
    public PlanVersion version(String personalAccount, UUID planId, int number) {
        return plans.findVersion(authored(personalAccount, planId).id(), number)
                .orElseThrow(PlanNotFoundException::new);
    }

    /** Publica o rascunho; a ficha que estava ativa para o aluno fica substituída (uma ativa por vez, FR-34). */
    @Transactional
    public WorkoutPlan publish(String personalAccount, UUID planId) {
        WorkoutPlan plan = authored(personalAccount, planId);
        clientAccess.requireActiveLink(personalAccount, plan.clientId());
        WorkoutPlan published = plan.publish(clock.instant());
        plans.findPublished(plan.clientId(), plan.personalId())
                .ifPresent(active -> plans.save(active.replace(clock.instant())));
        plans.save(published);
        return published;
    }

    /**
     * Fichas publicadas do personal que vencem nos próximos {@code withinDays} dias ou já venceram, só de alunos com
     * vínculo ativo.
     */
    public List<PlanRepository.ExpiringPlan> expiring(String personalAccount, Integer withinDays) {
        int days = withinDays == null ? DEFAULT_EXPIRING_DAYS : withinDays;
        if (days < 0 || days > MAX_EXPIRING_DAYS) {
            throw new InvalidPlanException("withinDays", "O período vai de 0 a 60 dias.");
        }
        UUID personalId = ViewExercise.accountId(personalAccount);
        if (personalId == null) {
            return List.of();
        }
        return plans.expiring(personalId, LocalDate.now(clock.withZone(ZONE)).plusDays(days));
    }

    /**
     * Cada exercício da ficha (principal e alternativas) precisa ser do catálogo ou próprio do autor, e não pode estar
     * arquivado, a menos que já estivesse na ficha (caso de borda: exercício arquivado depois continua nela).
     */
    private void requireUsableExercises(WorkoutPlan plan, Set<UUID> alreadyInPlan) {
        List<PlanVersion.Division> divisions = plan.current().divisions();
        for (int division = 0; division < divisions.size(); division++) {
            List<PlanItem> items = divisions.get(division).items();
            for (int index = 0; index < items.size(); index++) {
                String path = "divisions[" + division + "].items[" + index + "]";
                PlanItem item = items.get(index);
                requireUsable(plan, item.exerciseId(), alreadyInPlan, path + ".exerciseId");
                for (int alternative = 0; alternative < item.alternatives().size(); alternative++) {
                    requireUsable(
                            plan,
                            item.alternatives().get(alternative),
                            alreadyInPlan,
                            path + ".alternatives[" + alternative + "]");
                }
            }
        }
    }

    private void requireUsable(WorkoutPlan plan, UUID exerciseId, Set<UUID> alreadyInPlan, String field) {
        boolean usable = exercises
                .findById(exerciseId)
                .filter(exercise -> exercise.visibleTo(plan.personalId()))
                .filter(exercise -> !exercise.archived() || alreadyInPlan.contains(exerciseId))
                .isPresent();
        if (!usable) {
            throw new InvalidPlanException(
                    field, "Exercício indisponível: ele não existe, foi arquivado ou é de outro personal.");
        }
    }

    private WorkoutPlan authored(String personalAccount, UUID planId) {
        UUID personalId = ViewExercise.accountId(personalAccount);
        return plans.findById(planId)
                .filter(plan -> plan.authoredBy(personalId))
                .orElseThrow(PlanNotFoundException::new);
    }
}
