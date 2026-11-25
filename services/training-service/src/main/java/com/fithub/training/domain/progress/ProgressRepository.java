package com.fithub.training.domain.progress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consultas de evolução do treino (DEV-0091), calculadas na leitura das fichas, sessões e séries. */
public interface ProgressRepository {

    /**
     * As fichas já publicadas do aluno.
     *
     * @param personalId só as fichas deste personal; vazio para todas (a do personal e a própria)
     */
    List<PlanActivity> publishedPlans(UUID clientId, UUID personalId);

    /** Quando cada treino do aluno foi finalizado no intervalo, nas fichas do filtro. */
    List<Instant> finishedSessions(UUID clientId, UUID personalId, Instant from, Instant to);

    /** Por sessão finalizada no intervalo e exercício feito: a maior carga (com ao menos 1 repetição) e o volume. */
    List<ExerciseSession> exerciseSessions(UUID clientId, UUID personalId, Instant from, Instant to);

    /** O último treino finalizado de cada aluno nas fichas do personal; quem nunca treinou fica de fora. */
    Map<UUID, Instant> lastFinished(UUID personalId, Collection<UUID> clientIds);

    /**
     * Uma ficha publicada.
     *
     * @param replacedAt quando foi substituída; vazio enquanto continua valendo
     * @param divisions os treinos da versão atual
     */
    record PlanActivity(UUID planId, Instant firstPublishedAt, Instant replacedAt, int divisions) {}

    /**
     * Um exercício numa sessão finalizada.
     *
     * @param maxLoadKg a maior carga com ao menos 1 repetição; vazio se só houve séries sem repetição
     * @param volumeKg a soma de carga vezes repetições
     */
    record ExerciseSession(
            UUID sessionId, Instant finishedAt, UUID exerciseId, BigDecimal maxLoadKg, BigDecimal volumeKg) {}
}
