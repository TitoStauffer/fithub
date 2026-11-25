package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.LinkProjection;
import com.fithub.nutrition.domain.log.MealPhotoStorage;
import com.fithub.nutrition.domain.progress.DietAdherence;
import com.fithub.nutrition.domain.progress.ProgressPeriod;
import com.fithub.nutrition.domain.progress.ProgressRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Evolução da dieta (DEV-0092, FR-57, FR-58, FR-59): aderência, alternativas mais usadas, desvios e a última atividade
 * de cada paciente, calculados na leitura. A nutricionista com vínculo ativo vê os planos dela (BR-02); o paciente vê
 * os dele. O personal não tem rota (BR-03).
 */
@Service
public class DietProgress {

    /** O "hoje" e os dias são os do Brasil, não os do servidor. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** As alternativas mais usadas que a consulta traz. */
    public static final int TOP_ALTERNATIVES = 10;

    private final ProgressRepository progress;
    private final LinkProjection links;
    private final ClientAccess clientAccess;
    private final MealPhotoStorage storage;
    private final Clock clock;

    public DietProgress(
            ProgressRepository progress,
            LinkProjection links,
            ClientAccess clientAccess,
            MealPhotoStorage storage,
            Clock clock) {
        this.progress = progress;
        this.links = links;
        this.clientAccess = clientAccess;
        this.storage = storage;
        this.clock = clock;
    }

    /** Um desvio com a URL temporária da foto, quando ela existe. */
    public record DeviationView(ProgressRepository.Deviation deviation, URI photoUrl, Instant photoUrlExpiresAt) {}

    /** A evolução do período. */
    public record View(
            ProgressPeriod period,
            DietAdherence.Result adherence,
            List<ProgressRepository.AlternativeUse> alternatives,
            List<DeviationView> deviations) {}

    /** A última atividade de um paciente vinculado; vazia se ele nunca registrou refeição nos planos da nutricionista. */
    public record Activity(UUID clientId, Instant lastActivityAt) {}

    /** A evolução do paciente vinculado, nos planos da nutricionista. Sem vínculo ativo, 404. */
    public View ofClient(String nutritionistAccount, UUID clientId, LocalDate from, LocalDate to) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        return view(clientId, ViewFood.accountId(nutritionistAccount), from, to);
    }

    /** A evolução do próprio paciente, em todos os planos dele. */
    public View mine(String clientAccount, LocalDate from, LocalDate to) {
        return view(ViewFood.accountId(clientAccount), null, from, to);
    }

    /** A última atividade de cada paciente com vínculo ativo, nos planos da nutricionista (base da lista de inativos). */
    public List<Activity> activity(String nutritionistAccount) {
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        if (nutritionistId == null) {
            return List.of();
        }
        List<UUID> clients = links.activeClients(nutritionistId);
        Map<UUID, Instant> last = progress.lastLogged(nutritionistId, clients);
        return clients.stream()
                .map(client -> new Activity(client, last.get(client)))
                .toList();
    }

    private View view(UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to) {
        ProgressPeriod period = ProgressPeriod.of(from, to, LocalDate.now(clock.withZone(ZONE)));
        List<DietAdherence.PlanWindow> windows = progress.publishedPlans(clientId, nutritionistId).stream()
                .map(plan -> new DietAdherence.PlanWindow(
                        plan.planId(),
                        plan.replacedAt() == null ? null : day(plan.replacedAt()),
                        plan.versions().stream()
                                .map(version -> new DietAdherence.Version(day(version.publishedAt()), version.meals()))
                                .toList()))
                .toList();
        List<DietAdherence.Log> logs = progress.logs(clientId, nutritionistId, period.from(), period.to()).stream()
                .map(log -> new DietAdherence.Log(log.day(), log.planId(), log.status()))
                .toList();
        return new View(
                period,
                DietAdherence.of(period, windows, logs),
                progress.topAlternatives(clientId, nutritionistId, period.from(), period.to(), TOP_ALTERNATIVES),
                progress.deviations(clientId, nutritionistId, period.from(), period.to()).stream()
                        .map(this::withPhotoUrl)
                        .toList());
    }

    private DeviationView withPhotoUrl(ProgressRepository.Deviation deviation) {
        if (deviation.photoKey() == null) {
            return new DeviationView(deviation, null, null);
        }
        return new DeviationView(
                deviation,
                storage.presignDownload(deviation.photoKey()),
                clock.instant().plus(MealPhotoStorage.URL_VALIDITY));
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
