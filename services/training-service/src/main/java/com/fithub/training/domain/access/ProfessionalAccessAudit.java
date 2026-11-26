package com.fithub.training.domain.access;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registro de auditoria das leituras de dados sensíveis do aluno pelo personal vinculado (DEV-0170, NFR-03): quem leu,
 * de quem, o quê e quando. Nunca copia o dado lido. Fica 6 meses (ADR-012, item 6).
 */
public interface ProfessionalAccessAudit {

    /** O que foi lido. */
    enum Resource {
        MOBILITY_RESTRICTIONS
    }

    /** Uma leitura registrada, para a exportação dos dados do aluno. */
    record AccessRecord(UUID professionalId, Resource resource, Instant accessedAt) {}

    void record(UUID professionalId, UUID clientId, Resource resource, Instant at);

    /** Leituras dos dados do aluno, da mais nova para a mais antiga. */
    List<AccessRecord> logOf(UUID clientId);

    /** Tira o que foi registrado antes de {@code cutoff}. Devolve quantos registros saíram. */
    int purgeBefore(Instant cutoff);
}
