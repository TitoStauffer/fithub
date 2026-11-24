package com.fithub.training.application;

import com.fithub.training.domain.plan.PlanConflictException;
import com.fithub.training.domain.plan.PlanNotFoundException;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.WorkoutPlan;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ficha própria do aluno (DEV-0069, FR-52, QUESTÃO-03): sem personal, ou junto com ele, o aluno monta a ficha na
 * mesma estrutura (rascunho, publicação, versões) e treina nela. Só o catálogo serve de exercício. É só do aluno: o
 * personal não a vê, e ela não conta para a ficha ativa dele (uma ativa por autor).
 */
@Service
public class ManageOwnPlans {

    private final PlanRepository plans;
    private final ManagePlans managePlans;
    private final Clock clock;

    public ManageOwnPlans(PlanRepository plans, ManagePlans managePlans, Clock clock) {
        this.plans = plans;
        this.managePlans = managePlans;
        this.clock = clock;
    }

    public WorkoutPlan create(String clientAccount, ManagePlans.Draft draft) {
        WorkoutPlan plan = WorkoutPlan.newOwnDraft(
                clientId(clientAccount),
                draft.name(),
                draft.startsOn(),
                draft.weeks(),
                draft.divisions(),
                clock.instant());
        managePlans.requireUsableExercises(plan, Set.of());
        plans.save(plan);
        return plan;
    }

    /** As fichas próprias do aluno, da mais nova para a mais antiga. */
    public List<WorkoutPlan> list(String clientAccount) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        return clientId == null ? List.of() : plans.findByClientAndPersonal(clientId, null);
    }

    public WorkoutPlan view(String clientAccount, UUID planId) {
        return owned(clientAccount, planId);
    }

    /**
     * Edita a ficha própria: o rascunho no lugar; a publicada vira a versão seguinte (BR-09).
     *
     * @throws PlanConflictException se outra edição chegou antes
     */
    public WorkoutPlan update(String clientAccount, UUID planId, ManagePlans.Draft draft) {
        WorkoutPlan plan = owned(clientAccount, planId);
        WorkoutPlan edited = plan.edit(
                draft.baseVersion(), draft.name(), draft.startsOn(), draft.weeks(), draft.divisions(), clock.instant());
        managePlans.requireUsableExercises(edited, plan.current().exerciseIds());
        try {
            plans.save(edited);
        } catch (DataIntegrityViolationException sameVersion) {
            throw new PlanConflictException();
        }
        return edited;
    }

    /** Publica o rascunho; a ficha própria que estava ativa fica substituída. A do personal não muda. */
    @Transactional
    public WorkoutPlan publish(String clientAccount, UUID planId) {
        WorkoutPlan plan = owned(clientAccount, planId);
        WorkoutPlan published = plan.publish(clock.instant());
        plans.findPublished(plan.clientId(), null).ifPresent(active -> plans.save(active.replace(clock.instant())));
        plans.save(published);
        return published;
    }

    /** A ficha própria do aluno; a do personal e a de outro aluno respondem como inexistentes. */
    private WorkoutPlan owned(String clientAccount, UUID planId) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        return plans.findById(planId).filter(plan -> plan.ownedBy(clientId)).orElseThrow(PlanNotFoundException::new);
    }

    private static UUID clientId(String clientAccount) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        if (clientId == null) {
            throw new PlanNotFoundException();
        }
        return clientId;
    }
}
