package com.fithub.client.domain.professional;

import java.util.Objects;
import java.util.UUID;

/**
 * Profissional como o client-service o conhece (DEV-0035): projeção do {@code ProfissionalCadastrado}, mais o
 * código pessoal que ele compartilha com os clientes.
 *
 * @param id o {@code sub} da conta do profissional (ADR-009)
 * @param inviteCode código pessoal: permanente até o profissional trocá-lo
 */
public record Professional(UUID id, ProfessionalType type, String name, String inviteCode) {

    public Professional {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(inviteCode, "inviteCode");
    }
}
