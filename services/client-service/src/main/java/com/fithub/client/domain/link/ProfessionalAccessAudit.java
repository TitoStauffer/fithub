package com.fithub.client.domain.link;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoria das leituras de dados sensíveis do cliente pelo profissional vinculado (DEV-0040, DEV-0106,
 * NFR-03): quem leu, de quem, o quê e quando. Nunca copia o dado lido. Fica 6 meses (ADR-012, item 6).
 */
public interface ProfessionalAccessAudit {

    /** O que foi lido. */
    enum Resource {
        HEALTH,
        ASSESSMENTS,
        PHOTOS
    }

    void record(UUID professionalId, UUID clientId, Resource resource, Instant at);

    /** Tira o que foi registrado antes de {@code cutoff}. Devolve quantos registros saíram. */
    int purgeBefore(Instant cutoff);
}
