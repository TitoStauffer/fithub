package com.fithub.client.domain.professional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Projeção dos profissionais no client-service (porta do domínio). */
public interface ProfessionalDirectory {

    Optional<Professional> findById(UUID id);

    /** Profissional dono do código pessoal (já normalizado). */
    Optional<Professional> findByInviteCode(String code);

    /**
     * Grava o profissional vindo do {@code ProfissionalCadastrado}. Repetir o mesmo profissional atualiza nome
     * e tipo e mantém o código pessoal; o primeiro registro ganha um código novo.
     */
    void register(UUID id, ProfessionalType type, String name, Instant registeredAt);

    /** Troca o código pessoal do profissional; o antigo deixa de valer. Devolve o novo. */
    String rotateInviteCode(UUID id, Instant now);
}
