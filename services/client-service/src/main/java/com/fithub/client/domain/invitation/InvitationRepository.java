package com.fithub.client.domain.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos convites (porta do domínio). */
public interface InvitationRepository {

    /**
     * Grava o convite novo. Devolve {@code false} se o hash do token ou do código já existir (colisão,
     * praticamente impossível): quem chama gera outros segredos e tenta de novo.
     */
    boolean saveNew(Invitation invitation);

    Optional<Invitation> findById(UUID id);

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByCodeHash(String codeHash);

    /** Convites do profissional, do mais novo para o mais antigo. */
    List<Invitation> listByProfessional(UUID professionalId);

    /** Grava a mudança de situação (cancelamento). */
    void updateStatus(Invitation invitation);
}
