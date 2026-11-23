package com.fithub.client.domain.link;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoria das leituras do perfil de saúde pelo profissional (DEV-0040, NFR-03): quem leu, de quem
 * e quando. Nunca copia o dado lido.
 */
public interface HealthAccessAudit {

    void record(UUID professionalId, UUID clientId, Instant at);
}
