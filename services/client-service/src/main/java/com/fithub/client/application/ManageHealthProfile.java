package com.fithub.client.application;

import com.fithub.client.domain.profile.ClientProfileRepository;
import com.fithub.client.domain.profile.HealthProfile;
import com.fithub.client.domain.profile.HealthProfileRepository;
import com.fithub.client.domain.profile.ProfileRequiredException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Perfil de saúde da conta autenticada (DEV-0033): gravar e ler o próprio. */
@Service
public class ManageHealthProfile {

    private final ClientProfileRepository profiles;
    private final HealthProfileRepository repository;
    private final Clock clock;

    public ManageHealthProfile(ClientProfileRepository profiles, HealthProfileRepository repository, Clock clock) {
        this.profiles = profiles;
        this.repository = repository;
        this.clock = clock;
    }

    /** O que a pessoa enviou. {@code accountId} vem sempre do token, nunca do corpo da requisição. */
    public record Command(String accountId, List<HealthProfile.Condition> conditions, String notes) {}

    public HealthProfile save(Command command) {
        if (profiles.findByAccountId(command.accountId()).isEmpty()) {
            throw new ProfileRequiredException();
        }
        var now = clock.instant();
        // A data de criação que vale é a da primeira gravação: o repositório a mantém no upsert.
        return repository.save(HealthProfile.of(command.accountId(), command.conditions(), command.notes(), now, now));
    }

    public Optional<HealthProfile> findMine(String accountId) {
        return repository.findByAccountId(accountId);
    }
}
