package com.fithub.nutrition.domain.access;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Cópia local dos dados básicos dos clientes, vinda do {@code ClienteAtualizado} (DEV-0151). */
public interface ClientDirectory {

    /** Dados básicos do cliente como o client-service os publicou. Sem saúde, objetivo ou altura. */
    record ProjectedClient(UUID clientId, String name, LocalDate birthDate, String gender, Instant updatedAt) {}

    /**
     * Substitui a cópia local se o evento for mais novo que ela: um {@code ClienteAtualizado} atrasado não
     * desfaz uma mudança mais recente.
     */
    void update(ProjectedClient client);

    Optional<ProjectedClient> find(UUID clientId);

    /** Apaga a cópia local do cliente (DEV-0153): sem vínculo ativo no serviço, ela não é mais necessária. */
    void remove(UUID clientId);
}
