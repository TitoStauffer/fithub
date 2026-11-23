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

    /**
     * Marca o convite como aceito se ele ainda estiver pendente e dentro da validade, numa instrução só: de dois
     * aceites simultâneos, só um consegue (uso único). Devolve {@code false} se o convite já não valia.
     */
    boolean markAccepted(UUID id, java.time.Instant now);

    /** Grava a mudança de situação (cancelamento). */
    void updateStatus(Invitation invitation);
}
