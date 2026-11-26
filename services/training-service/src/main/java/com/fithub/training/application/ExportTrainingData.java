package com.fithub.training.application;

import com.fithub.training.domain.access.ProfessionalAccessAudit;
import com.fithub.training.domain.account.TrainingExportReader;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
import com.fithub.training.domain.restriction.MobilityRestriction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Exportação dos dados que o training tem do cliente (DEV-0166, NFR-01, LGPD art. 18, II e V): as fichas (as dos
 * personais só nas versões publicadas, que são as que o cliente vê; a própria, com os rascunhos), as sessões com as
 * séries e os comentários, os recordes e as restrições de mobilidade. A conta vem sempre do token.
 */
@Service
public class ExportTrainingData {

    private final TrainingExportReader reader;
    private final PlanRepository plans;
    private final ClientRecords records;
    private final ViewOwnRestrictions restrictions;
    private final ProfessionalAccessAudit audit;
    private final Clock clock;

    public ExportTrainingData(
            TrainingExportReader reader,
            PlanRepository plans,
            ClientRecords records,
            ViewOwnRestrictions restrictions,
            ProfessionalAccessAudit audit,
            Clock clock) {
        this.reader = reader;
        this.plans = plans;
        this.records = records;
        this.restrictions = restrictions;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ficha com as versões que o cliente pode ver. */
    public record PlanWithVersions(WorkoutPlan plan, List<PlanVersion> versions) {}

    public record Export(
            Instant exportedAt,
            List<PlanWithVersions> plans,
            List<TrainingExportReader.ExportedSession> sessions,
            ClientRecords.Records records,
            List<MobilityRestriction> restrictions,
            List<ProfessionalAccessAudit.AccessRecord> accessLog) {}

    public Export mine(String clientAccount) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        if (clientId == null) {
            return new Export(
                    clock.instant(),
                    List.of(),
                    List.of(),
                    records.mine(clientAccount),
                    restrictions.list(clientAccount),
                    List.of());
        }
        List<PlanWithVersions> exportedPlans = reader.planIdsOf(clientId).stream()
                .flatMap(id -> plans.findById(id).stream())
                // O rascunho da ficha do personal ainda não foi entregue ao cliente.
                .filter(plan -> plan.personalId() == null || plan.status() != WorkoutPlan.Status.DRAFT)
                .map(this::withVersions)
                .toList();
        return new Export(
                clock.instant(),
                exportedPlans,
                reader.sessionsOf(clientId),
                records.mine(clientAccount),
                restrictions.list(clientAccount),
                audit.logOf(clientId));
    }

    private PlanWithVersions withVersions(WorkoutPlan plan) {
        boolean own = plan.personalId() == null;
        List<PlanVersion> versions = plans.findVersions(plan.id()).stream()
                .filter(summary -> own || summary.publishedAt() != null)
                .flatMap(summary -> plans.findVersion(plan.id(), summary.number()).stream())
                .toList();
        return new PlanWithVersions(plan, versions);
    }
}
