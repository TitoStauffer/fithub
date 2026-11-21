package com.fithub.training.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD: Plano de treino versionado
 */
class WorkoutPlanTest {

    @Test
    void shouldCreateVersionedWorkoutPlan() {
        UUID patientId = UUID.randomUUID();
        UUID ptId = UUID.randomUUID();

        WorkoutPlan plan = WorkoutPlan.create(patientId, ptId);

        assertNotNull(plan.getId());
        assertEquals(patientId, plan.getPatientId());
        assertEquals(ptId, plan.getPtId());
        assertEquals(1, plan.getVersion());
        assertEquals("ACTIVE", plan.getStatus());
        assertTrue(plan.getIsActive());
    }

    @Test
    void shouldStartWithEmptyDays() {
        WorkoutPlan plan = WorkoutPlan.create(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(0, plan.getDays().size());
    }

    @Test
    void shouldAddExerciseToDay() {
        WorkoutPlan plan = WorkoutPlan.create(UUID.randomUUID(), UUID.randomUUID());
        UUID exerciseId = UUID.randomUUID();
        WorkoutExerciseDetails exercise = WorkoutExerciseDetails.create(
            exerciseId, 4, 10, 80.0, "https://example.com/video.mp4", "Push hard", 1
        );

        plan.addExerciseToDay("MONDAY", exercise);

        assertEquals(1, plan.getDays().get("MONDAY").size());
    }

    @Test
    void shouldPreserveVersion() {
        WorkoutPlan planV1 = WorkoutPlan.create(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(1, planV1.getVersion());
    }
}
