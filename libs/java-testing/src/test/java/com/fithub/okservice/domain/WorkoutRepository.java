package com.fithub.okservice.domain;

/** Porta do domínio, implementada na infraestrutura. */
public interface WorkoutRepository {
    void save(Workout workout);
}
