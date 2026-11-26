package com.fithub.training.domain.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Leitura do que o training tem do cliente, para a exportação dos dados dele (porta do domínio, DEV-0166). */
public interface TrainingExportReader {

    /** Uma série registrada, com o nome do exercício. */
    record ExportedSet(
            int itemPosition,
            int setNumber,
            UUID exerciseId,
            String exerciseName,
            BigDecimal loadKg,
            int reps,
            Instant recordedAt) {}

    /** Uma sessão de treino, aberta ou terminada, com o comentário, a sensação e as séries. */
    record ExportedSession(
            UUID id,
            UUID planId,
            int versionNumber,
            int divisionPosition,
            Instant startedAt,
            Instant finishedAt,
            String comment,
            Integer feeling,
            List<ExportedSet> sets) {}

    /** Ids das fichas do cliente: as dos personais e a própria, em qualquer estado, da mais nova para a mais antiga. */
    List<UUID> planIdsOf(UUID clientId);

    /** Sessões do cliente, da mais nova para a mais antiga, cada uma com as séries na ordem do treino. */
    List<ExportedSession> sessionsOf(UUID clientId);
}
