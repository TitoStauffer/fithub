package com.fithub.training.domain.restriction;

import com.fithub.training.domain.exercise.MuscleGroup;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Regiões do corpo que cada grupo muscular envolve (DEV-0062, FR-27), para alertar o personal quando um exercício da
 * ficha toca uma região restrita do aluno. Alerta, não bloqueio (BR-11): o personal é o responsável técnico.
 *
 * <p>Decisão do Agent na ausência do Tito, para revisão: o mapa abaixo, versionado no código.
 */
public final class MuscleRegions {

    private MuscleRegions() {}

    private static final Map<MuscleGroup, Set<BodyRegion>> REGIONS = new EnumMap<>(MuscleGroup.class);

    static {
        REGIONS.put(MuscleGroup.QUADRICEPS, EnumSet.of(BodyRegion.KNEE, BodyRegion.HIP));
        REGIONS.put(MuscleGroup.HAMSTRINGS, EnumSet.of(BodyRegion.KNEE, BodyRegion.HIP));
        REGIONS.put(MuscleGroup.GLUTES, EnumSet.of(BodyRegion.HIP, BodyRegion.KNEE));
        REGIONS.put(MuscleGroup.ADDUCTORS, EnumSet.of(BodyRegion.HIP, BodyRegion.KNEE));
        REGIONS.put(MuscleGroup.CALVES, EnumSet.of(BodyRegion.ANKLE_FOOT, BodyRegion.KNEE));
        REGIONS.put(MuscleGroup.CHEST, EnumSet.of(BodyRegion.SHOULDER, BodyRegion.ELBOW));
        REGIONS.put(MuscleGroup.SHOULDERS, EnumSet.of(BodyRegion.SHOULDER, BodyRegion.ELBOW));
        REGIONS.put(MuscleGroup.TRICEPS, EnumSet.of(BodyRegion.ELBOW, BodyRegion.SHOULDER));
        REGIONS.put(MuscleGroup.BICEPS, EnumSet.of(BodyRegion.ELBOW, BodyRegion.SHOULDER));
        REGIONS.put(MuscleGroup.FOREARMS, EnumSet.of(BodyRegion.WRIST_HAND, BodyRegion.ELBOW));
        REGIONS.put(MuscleGroup.BACK, EnumSet.of(BodyRegion.SHOULDER, BodyRegion.THORACIC));
        REGIONS.put(MuscleGroup.LOWER_BACK, EnumSet.of(BodyRegion.LUMBAR));
        REGIONS.put(MuscleGroup.ABS, EnumSet.of(BodyRegion.LUMBAR));
        REGIONS.put(MuscleGroup.NECK, EnumSet.of(BodyRegion.CERVICAL));
        REGIONS.put(MuscleGroup.FULL_BODY, EnumSet.allOf(BodyRegion.class));
    }

    /** As regiões que os grupos musculares de um exercício envolvem. */
    public static Set<BodyRegion> of(Set<MuscleGroup> groups) {
        Set<BodyRegion> regions = EnumSet.noneOf(BodyRegion.class);
        groups.forEach(group -> regions.addAll(REGIONS.getOrDefault(group, Set.of())));
        return regions;
    }
}
