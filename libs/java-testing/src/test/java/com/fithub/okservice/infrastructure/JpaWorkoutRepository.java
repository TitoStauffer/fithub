package com.fithub.okservice.infrastructure;

import com.fithub.okservice.domain.Workout;
import com.fithub.okservice.domain.WorkoutRepository;

import jakarta.persistence.EntityManager;

public class JpaWorkoutRepository implements WorkoutRepository {
    private EntityManager entityManager;

    @Override
    public void save(Workout workout) {
        entityManager.persist(workout);
    }
}
