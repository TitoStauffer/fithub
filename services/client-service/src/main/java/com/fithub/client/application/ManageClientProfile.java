package com.fithub.client.application;

import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.client.domain.profile.ClientProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Perfil do cliente da conta autenticada (DEV-0032): criar, atualizar e ler o próprio perfil. */
@Service
public class ManageClientProfile {

    private final ClientProfileRepository repository;
    private final Clock clock;

    public ManageClientProfile(ClientProfileRepository repository, Clock clock) {
        this.repository = repository;
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
        Optional<ClientProfile> existing = repository.findByAccountId(command.accountId());
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
        return new Result(saved.profile(), saved.created(), saved.profile().ageOn(today));
    }

    public Optional<Result> findMine(String accountId) {
        LocalDate today = LocalDate.now(clock);
        return repository.findByAccountId(accountId).map(profile -> new Result(profile, false, profile.ageOn(today)));
    }
}
