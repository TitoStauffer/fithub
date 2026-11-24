package com.fithub.training.domain.record;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Marcas do aluno para os recordes: as séries registradas e os lançamentos manuais (DEV-0068). */
public interface RecordRepository {

    /**
     * Todas as marcas do aluno com ao menos 1 repetição: as séries (com a data do treino no fuso do Brasil) e os
     * lançamentos manuais.
     */
    List<PersonalRecords.Performance> performancesOf(UUID clientId);

    void saveManual(ManualRecord record);

    Optional<ManualRecord> findManual(UUID id);

    void deleteManual(UUID id);
}
