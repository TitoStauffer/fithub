package com.fithub.okservice.application;

import com.fithub.okservice.domain.Workout;
import com.fithub.okservice.domain.WorkoutRepository;

public class StartWorkout {
    private final WorkoutRepository repository;

    public StartWorkout(WorkoutRepository repository) {
        this.repository = repository;
    }

    public void start(String name) {
        repository.save(new Workout(name));
    }
}
