package com.fithub.training.application;

import com.fithub.training.domain.access.LinkProjection;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.plan.PlanItem;
import com.fithub.training.domain.plan.PlanVersion;
import com.fithub.training.domain.plan.WorkoutPlan;
import com.fithub.training.domain.restriction.BodyRegion;
import com.fithub.training.domain.restriction.MobilityRestriction;
import com.fithub.training.domain.restriction.MuscleRegions;
import com.fithub.training.domain.restriction.RestrictionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Alertas de restrição de mobilidade na ficha (DEV-0062, FR-27, BR-11): cada exercício (principal ou alternativa)
 * que envolve uma região restrita do aluno gera um alerta. Nunca bloqueia salvar nem publicar.
 *
 * <p>O alerta é calculado na hora, com as restrições atuais do aluno, e só aparece com vínculo ativo: depois que o
 * vínculo acaba, o autor ainda lê a ficha (BR-05), mas não as restrições do aluno.
 */
@Service
public class PlanAlerts {

    private final LinkProjection links;
    private final RestrictionRepository restrictions;
    private final ExerciseRepository exercises;

    public PlanAlerts(LinkProjection links, RestrictionRepository restrictions, ExerciseRepository exercises) {
        this.links = links;
        this.restrictions = restrictions;
        this.exercises = exercises;
    }

    /**
     * Um exercício da ficha que envolve uma região restrita.
     *
     * @param alternative se o exercício é uma alternativa do item, não o principal
     */
    public record Alert(
            int divisionPosition,
            String divisionLetter,
            int itemPosition,
            UUID exerciseId,
            boolean alternative,
            BodyRegion region,
            UUID restrictionId,
            String description) {}

    /** Os alertas da versão atual da ficha, na ordem das divisões e dos itens. */
    public List<Alert> of(WorkoutPlan plan) {
        if (!links.hasActiveLink(plan.personalId(), plan.clientId())) {
            return List.of();
        }
        List<MobilityRestriction> restricted = restrictions.findByClient(plan.clientId());
        if (restricted.isEmpty()) {
            return List.of();
        }
        Map<UUID, Optional<Exercise>> cache = new HashMap<>();
        List<Alert> alerts = new ArrayList<>();
        List<PlanVersion.Division> divisions = plan.current().divisions();
        for (int division = 0; division < divisions.size(); division++) {
            List<PlanItem> items = divisions.get(division).items();
            for (int index = 0; index < items.size(); index++) {
                Position at = new Position(division, divisions.get(division).letter(), index);
                alerts.addAll(alertsFor(at, items.get(index), restricted, cache));
            }
        }
        return alerts;
    }

    private record Position(int division, String letter, int item) {}

    /** Os alertas de um item: o exercício principal e cada alternativa contra cada restrição. */
    private List<Alert> alertsFor(
            Position at, PlanItem item, List<MobilityRestriction> restricted, Map<UUID, Optional<Exercise>> cache) {
        List<Alert> alerts = new ArrayList<>();
        List<UUID> ids = item.exerciseIds();
        for (int position = 0; position < ids.size(); position++) {
            UUID exerciseId = ids.get(position);
            Set<BodyRegion> regions = cache.computeIfAbsent(exerciseId, exercises::findById)
                    .map(exercise -> MuscleRegions.of(exercise.muscleGroups()))
                    .orElse(Set.of());
            boolean alternative = position > 0;
            restricted.stream()
                    .filter(restriction -> regions.contains(restriction.region()))
                    .map(restriction -> new Alert(
                            at.division(),
                            at.letter(),
                            at.item(),
                            exerciseId,
                            alternative,
                            restriction.region(),
                            restriction.id(),
                            restriction.description()))
                    .forEach(alerts::add);
        }
        return alerts;
    }
}
