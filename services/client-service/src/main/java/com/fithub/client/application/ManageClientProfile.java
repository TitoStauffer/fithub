package com.fithub.client.application;

import com.fithub.client.domain.profile.ClientEvents;
import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.client.domain.profile.ClientProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Perfil do cliente da conta autenticada (DEV-0032): criar, atualizar e ler o próprio perfil. Quando os
 * dados básicos mudam, grava o {@code ClienteAtualizado} no outbox na mesma transação (DEV-0034).
 */
@Service
public class ManageClientProfile {

    private final ClientProfileRepository repository;
    private final ClientEvents events;
    private final TransactionOperations transaction;
    private final Clock clock;

    public ManageClientProfile(
            ClientProfileRepository repository, ClientEvents events, TransactionOperations transaction, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** O que a pessoa enviou. {@code accountId} vem sempre do token, nunca do corpo da requisição. */
    public record Command(
            String accountId,
            String name,
            LocalDate birthDate,
            ClientProfile.Gender gender,
            Integer heightCm,
            ClientProfile.Goal goal) {}

    /** Perfil gravado, se ele foi criado agora, e a idade calculada hoje. */
    public record Result(ClientProfile profile, boolean created, int age) {}

    public Result save(Command command) {
        var now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        return transaction.execute(status -> {
            // Trava o perfil existente: a comparação abaixo vê o estado que esta gravação substitui.
            Optional<ClientProfile> existing = repository.findByAccountIdForUpdate(command.accountId());
            var profile = ClientProfile.of(
                    command.accountId(),
                    command.name(),
                    command.birthDate(),
                    command.gender(),
                    command.heightCm() == null ? 0 : command.heightCm(),
                    command.goal(),
                    today,
                    existing.map(ClientProfile::createdAt).orElse(now),
                    now);
            ClientProfileRepository.Saved saved = repository.save(profile);
            // Sem o antes (perfil novo, ou criado por outro pedido no mesmo instante) o evento sai: um evento a
            // mais é inofensivo, porque o consumidor substitui a cópia local; um a menos deixaria a cópia velha.
            boolean sharedDataChanged = existing.map(before -> !before.sameSharedDataAs(saved.profile()))
                    .orElse(true);
            if (sharedDataChanged) {
                events.updated(saved.profile());
            }
            return new Result(saved.profile(), saved.created(), saved.profile().ageOn(today));
        });
    }

    public Optional<Result> findMine(String accountId) {
        LocalDate today = LocalDate.now(clock);
        return repository.findByAccountId(accountId).map(profile -> new Result(profile, false, profile.ageOn(today)));
    }
}
