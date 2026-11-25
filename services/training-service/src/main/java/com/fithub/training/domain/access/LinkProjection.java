package com.fithub.training.domain.access;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Projeção local dos vínculos entre clientes e personais (DEV-0039, ADR-003): decide o acesso sem perguntar ao
 * client-service. Só vínculos de personal chegam aqui (BR-03).
 */
public interface LinkProjection {

    /** Vínculo ativado. Não reativa um vínculo que já chegou encerrado (eventos fora de ordem). */
    void activate(UUID linkId, UUID clientId, UUID professionalId, Instant occurredAt);

    /** Vínculo encerrado; registra o encerramento mesmo que a ativação ainda não tenha chegado. */
    void end(UUID linkId, UUID clientId, UUID professionalId, Instant occurredAt);

    /** Se o profissional tem vínculo ativo com o cliente. */
    boolean hasActiveLink(UUID professionalId, UUID clientId);

    /** Os alunos com vínculo ativo com o personal (DEV-0091). */
    List<UUID> activeClients(UUID professionalId);
}
