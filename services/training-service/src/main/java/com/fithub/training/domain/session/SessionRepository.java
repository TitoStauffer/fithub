package com.fithub.training.domain.session;

import java.util.Optional;
import java.util.UUID;

/** Sessões de treino dos alunos (DEV-0065). */
public interface SessionRepository {

    /** Grava a sessão; outra sessão aberta na mesma ficha bate no índice único. */
    void save(WorkoutSession session);

    Optional<WorkoutSession> findById(UUID id);

    /** A última sessão finalizada da ficha, pela data de fim. */
    Optional<WorkoutSession> lastFinished(UUID planId);

    /** A sessão aberta da ficha, se houver. */
    Optional<WorkoutSession> open(UUID planId);
}
