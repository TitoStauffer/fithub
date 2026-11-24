package com.fithub.training.domain.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Séries registradas (DEV-0066). */
public interface SetRepository {

    /**
     * Grava ou corrige a série pelo id; outra série no mesmo lugar (item e número) bate na unicidade.
     *
     * @return {@code false} se o id já é de uma série de outra sessão (nada é gravado)
     */
    boolean save(WorkoutSet set);

    Optional<WorkoutSet> findById(UUID id);

    void delete(UUID id);

    /** As séries da sessão, por item e número. */
    List<WorkoutSet> findBySession(UUID sessionId);

    /**
     * As séries do último treino finalizado do aluno com o exercício, sem contar a sessão atual, por número.
     */
    List<WorkoutSet> lastWithExercise(UUID clientId, UUID exerciseId, UUID exceptSession);
}
