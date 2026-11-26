package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.ProfessionalAccessAudit;
import com.fithub.nutrition.domain.account.NutritionExportReader;
import com.fithub.nutrition.domain.log.MealLogRepository;
import com.fithub.nutrition.domain.restriction.FoodRestriction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Exportação dos dados que o nutrition tem do cliente (DEV-0167, NFR-01, LGPD art. 18, II e V): os planos alimentares
 * recebidos (todas as versões, com refeições, alimentos e macros), os registros de refeição com trocas, desvios e a
 * foto do prato por URL temporária, e as restrições alimentares. A conta vem sempre do token.
 */
@Service
public class ExportNutritionData {

    private final NutritionExportReader reader;
    private final ManageMealPlans plans;
    private final MealLogRepository logs;
    private final MealLogPhotos photos;
    private final ViewOwnFoodRestrictions restrictions;
    private final ProfessionalAccessAudit audit;
    private final Clock clock;

    public ExportNutritionData(
            NutritionExportReader reader,
            ManageMealPlans plans,
            MealLogRepository logs,
            MealLogPhotos photos,
            ViewOwnFoodRestrictions restrictions,
            ProfessionalAccessAudit audit,
            Clock clock) {
        this.reader = reader;
        this.plans = plans;
        this.logs = logs;
        this.photos = photos;
        this.restrictions = restrictions;
        this.audit = audit;
        this.clock = clock;
    }

    public record Export(
            Instant exportedAt,
            List<ManageMealPlans.PlanView> planVersions,
            List<MealLogPhotos.LogView> logs,
            List<FoodRestriction> restrictions,
            List<ProfessionalAccessAudit.AccessRecord> accessLog) {}

    public Export mine(String clientAccount) {
        UUID clientId = ViewFood.accountId(clientAccount);
        if (clientId == null) {
            return new Export(clock.instant(), List.of(), List.of(), restrictions.mine(clientAccount), List.of());
        }
        List<ManageMealPlans.PlanView> planVersions = reader.deliveredPlanIdsOf(clientId).stream()
                .flatMap(planId -> plans.clientVersions(clientAccount, planId).stream()
                        .map(summary -> plans.clientVersion(clientAccount, planId, summary.number())))
                .toList();
        List<MealLogPhotos.LogView> exportedLogs = reader.logIdsOf(clientId).stream()
                .flatMap(id -> logs.findById(id).stream())
                .map(photos::view)
                .toList();
        return new Export(
                clock.instant(), planVersions, exportedLogs, restrictions.mine(clientAccount), audit.logOf(clientId));
    }
}
