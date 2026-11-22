package com.fithub.okservice.interfaces;

import org.springframework.web.bind.annotation.RestController;

import com.fithub.okservice.application.StartWorkout;

@RestController
public class WorkoutController {
    private final StartWorkout startWorkout;

    public WorkoutController(StartWorkout startWorkout) {
        this.startWorkout = startWorkout;
    }

    public void start() {
        startWorkout.start("treino");
    }
}
