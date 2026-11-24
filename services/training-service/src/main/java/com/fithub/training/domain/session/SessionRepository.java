package com.fithub.training.domain.session;

import java.util.Optional;
import java.util.UUID;

/** Sessões de treino dos alunos (DEV-0065). */
public interface SessionRepository {

    /**
     * Grava a sessão nova ou a finalização; outra sessão aberta na mesma ficha bate no índice único. Uma sessão já
     * finalizada nunca volta a ficar aberta, e o comentário e a sensação não são regravados aqui.
     */
    void save(WorkoutSession session);

    /** Grava só o comentário e a sensação (DEV-0067), sem tocar na finalização. */
    void saveFeedback(WorkoutSession session);

    Optional<WorkoutSession> findById(UUID id);

    /** A última sessão finalizada da ficha, pela data de fim. */
    Optional<WorkoutSession> lastFinished(UUID planId);

    /** A sessão aberta da ficha, se houver. */
    Optional<WorkoutSession> open(UUID planId);
}
