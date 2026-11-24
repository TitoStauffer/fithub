package com.fithub.training.domain.restriction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.training.domain.exercise.MuscleGroup;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mapa grupo muscular → regiões do corpo (DEV-0062). */
class MuscleRegionsTest {

    @Test
    void exercicioDePernaEnvolveJoelhoEQuadril() {
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.QUADRICEPS)))
                .containsExactlyInAnyOrder(BodyRegion.KNEE, BodyRegion.HIP);
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.CALVES)))
                .containsExactlyInAnyOrder(BodyRegion.ANKLE_FOOT, BodyRegion.KNEE);
    }

    @Test
    void cadaGrupoTemAsRegioesDoMapa() {
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.CHEST))).doesNotContain(BodyRegion.KNEE, BodyRegion.LUMBAR);
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.LOWER_BACK))).containsExactly(BodyRegion.LUMBAR);
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.NECK))).containsExactly(BodyRegion.CERVICAL);
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.FOREARMS)))
                .containsExactlyInAnyOrder(BodyRegion.WRIST_HAND, BodyRegion.ELBOW);
        assertThat(MuscleRegions.of(Set.of(MuscleGroup.FULL_BODY))).containsExactlyInAnyOrder(BodyRegion.values());
        assertThat(MuscleRegions.of(Set.of())).isEmpty();
    }

    @Test
    void todoGrupoMuscularTemAoMenosUmaRegiao() {
        for (MuscleGroup group : MuscleGroup.values()) {
            assertThat(MuscleRegions.of(Set.of(group))).as(group.name()).isNotEmpty();
        }
    }
}
