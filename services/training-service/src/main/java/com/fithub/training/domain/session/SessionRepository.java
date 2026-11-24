package com.fithub.training.domain.session;

import java.time.Instant;
import java.util.List;
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

    /** As sessões finalizadas do aluno, da mais nova para a mais antiga, a partir de {@code offset} (DEV-0072). */
    List<FinishedSummary> finished(UUID clientId, int offset, int limit);

    /** Quantas sessões finalizadas o aluno tem. */
    long countFinished(UUID clientId);

    /**
     * Resumo de um treino feito, para o histórico: a ficha e o treino na versão da sessão, e quantas séries teve.
     *
     * @param divisionName o nome do treino na versão da sessão; pode faltar
     */
    record FinishedSummary(
            UUID id,
            UUID planId,
            String planName,
            int divisionPosition,
            String divisionLetter,
            String divisionName,
            Instant startedAt,
            Instant finishedAt,
            int setCount,
            Integer feeling) {}
}
