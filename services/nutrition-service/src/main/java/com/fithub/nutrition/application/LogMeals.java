package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.log.InvalidMealLogException;
import com.fithub.nutrition.domain.log.MealLog;
import com.fithub.nutrition.domain.log.MealLogConflictException;
import com.fithub.nutrition.domain.log.MealLogNotFoundException;
import com.fithub.nutrition.domain.log.MealLogRepository;
import com.fithub.nutrition.domain.plan.MealPlan;
import com.fithub.nutrition.domain.plan.MealPlanNotFoundException;
import com.fithub.nutrition.domain.plan.MealPlanRepository;
import com.fithub.nutrition.domain.plan.MealPlanVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Refeições de hoje e o registro delas pelo paciente (DEV-0087, FR-50). Só no plano publicado do próprio paciente; o
 * registro fica na versão em que foi feito (BR-09) e só é corrigido ou apagado no mesmo dia (BR-08, QUESTÃO-04).
 */
@Service
public class LogMeals {

    /** O "hoje" do registro é o do Brasil, não o do servidor. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final MealPlanRepository plans;
    private final MealLogRepository logs;
    private final ManageMealPlans managePlans;
    private final MealLogPhotos photos;
    private final Clock clock;

    public LogMeals(
            MealPlanRepository plans,
            MealLogRepository logs,
            ManageMealPlans managePlans,
            MealLogPhotos photos,
            Clock clock) {
        this.plans = plans;
        this.logs = logs;
        this.managePlans = managePlans;
        this.photos = photos;
        this.clock = clock;
    }

    /** O plano publicado, na versão atual e sem os alertas (que são da nutricionista), e os registros de hoje. */
    public record Today(ManageMealPlans.PlanView plan, List<MealLogPhotos.LogView> logs) {}

    /** O que o app manda para registrar ou corrigir uma refeição. */
    public record LogDraft(
            UUID planId,
            Integer mealPosition,
            MealLog.Status status,
            List<MealLog.Substitution> substitutions,
            String deviation) {}

    public Today today(String clientAccount) {
        UUID clientId = client(clientAccount);
        MealPlan plan = plans.findPublished(clientId).orElseThrow(MealPlanNotFoundException::new);
        return new Today(
                managePlans.clientView(plan),
                logs.findByPlanAndDay(clientId, plan.id(), today()).stream()
                        .map(photos::view)
                        .toList());
    }

    /**
     * Registra a refeição de hoje ou corrige o registro com o mesmo id.
     *
     * @throws MealLogConflictException se o id é de outro paciente, de outro plano ou de outro dia, se a refeição já foi
     *     registrada hoje ou se o plano foi substituído
     */
    public MealLogPhotos.LogView record(String clientAccount, UUID logId, LogDraft draft) {
        UUID clientId = client(clientAccount);
        if (draft.planId() == null) {
            throw new InvalidMealLogException("planId", "Informe o plano.");
        }
        Optional<MealLog> existing = logs.findById(logId);
        if (existing.isPresent()) {
            return photos.view(correct(existing.get(), clientId, draft));
        }
        MealPlan plan = plans.findById(draft.planId())
                .filter(found -> found.clientId().equals(clientId))
                .filter(found -> found.status() != MealPlan.Status.DRAFT)
                .orElseThrow(MealPlanNotFoundException::new);
        if (plan.status() == MealPlan.Status.REPLACED) {
            throw new MealLogConflictException("Este plano foi substituído: registre no plano atual.");
        }
        MealLog log = MealLog.record(
                logId,
                clientId,
                plan.id(),
                plan.current(),
                draft.mealPosition(),
                draft.status(),
                draft.substitutions(),
                draft.deviation(),
                today(),
                clock.instant());
        try {
            logs.add(log);
        } catch (DataIntegrityViolationException taken) {
            // A refeição já tem registro hoje (unicidade no banco), inclusive quando dois envios chegam juntos.
            throw new MealLogConflictException("Esta refeição já foi registrada hoje.");
        }
        return photos.view(log);
    }

    private MealLog correct(MealLog log, UUID clientId, LogDraft draft) {
        if (!log.clientId().equals(clientId) || !log.planId().equals(draft.planId())) {
            throw new MealLogConflictException("Este id já é de outro registro.");
        }
        MealPlanVersion version =
                plans.findVersion(log.planId(), log.versionNumber()).orElseThrow(MealPlanNotFoundException::new);
        MealLog corrected = log.correct(
                version,
                draft.mealPosition(),
                draft.status(),
                draft.substitutions(),
                draft.deviation(),
                today(),
                clock.instant());
        logs.update(corrected);
        return corrected;
    }

    /** Apaga o registro do paciente, só no dia em que foi feito. */
    public void remove(String clientAccount, UUID logId) {
        UUID clientId = client(clientAccount);
        MealLog log = logs.findById(logId)
                .filter(found -> found.clientId().equals(clientId))
                .orElseThrow(MealLogNotFoundException::new);
        log.requireCorrectableOn(today());
        logs.delete(log.id());
        photos.deleteFilesOf(log);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** Conta sem {@code sub} em UUID vira vazio: não acha plano (404) e não é dona de registro (409 ou 404). */
    private static UUID client(String account) {
        return ViewFood.accountId(account);
    }
}
