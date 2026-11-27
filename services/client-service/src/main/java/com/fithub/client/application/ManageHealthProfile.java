package com.fithub.client.application;

import com.fithub.client.domain.profile.ClientProfileRepository;
import com.fithub.client.domain.profile.HealthConsentTerms;
import com.fithub.client.domain.profile.HealthConsents;
import com.fithub.client.domain.profile.HealthProfile;
import com.fithub.client.domain.profile.HealthProfileRepository;
import com.fithub.client.domain.profile.InvalidProfileException;
import com.fithub.client.domain.profile.ProfileRequiredException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil de saúde da conta autenticada (DEV-0033): gravar e ler o próprio. Gravar exige o aceite do termo vigente do
 * perfil de saúde (DEV-0179): na primeira vez, o pedido traz o aceite, e ele fica gravado com a versão e o momento.
 */
@Service
public class ManageHealthProfile {

    private final ClientProfileRepository profiles;
    private final HealthProfileRepository repository;
    private final HealthConsents consents;
    private final Clock clock;

    public ManageHealthProfile(
            ClientProfileRepository profiles,
            HealthProfileRepository repository,
            HealthConsents consents,
            Clock clock) {
        this.profiles = profiles;
        this.repository = repository;
        this.consents = consents;
        this.clock = clock;
    }

    /**
     * O que a pessoa enviou. {@code accountId} vem sempre do token, nunca do corpo da requisição. O aceite só é exigido
     * enquanto a conta não aceitou a versão vigente do termo.
     */
    public record Command(
            String accountId,
            List<HealthProfile.Condition> conditions,
            String notes,
            Boolean consent,
            String termsVersion) {}

    /** O termo vigente e se a conta já o aceitou, para a tela mostrar o texto na primeira vez. */
    public record Terms(String version, String text, boolean accepted) {}

    /** O termo do perfil de saúde mudou depois que a tela o mostrou: o cliente precisa ler a versão nova. */
    public static final class TermsOutdatedException extends RuntimeException {

        public TermsOutdatedException() {
            super("O termo do perfil de saúde mudou. Leia a versão atual antes de salvar.");
        }
    }

    @Transactional
    public HealthProfile save(Command command) {
        if (profiles.findByAccountId(command.accountId()).isEmpty()) {
            throw new ProfileRequiredException();
        }
        var now = clock.instant();
        if (!consents.accepted(command.accountId(), HealthConsentTerms.CURRENT_VERSION)) {
            if (!Boolean.TRUE.equals(command.consent())) {
                throw new InvalidProfileException(
                        "consent", "Para salvar o perfil de saúde, aceite o termo do perfil de saúde.");
            }
            if (!HealthConsentTerms.isCurrent(command.termsVersion())) {
                throw new TermsOutdatedException();
            }
            consents.accept(command.accountId(), HealthConsentTerms.CURRENT_VERSION, now);
        }
        // A data de criação que vale é a da primeira gravação: o repositório a mantém no upsert.
        return repository.save(HealthProfile.of(command.accountId(), command.conditions(), command.notes(), now, now));
    }

    public Optional<HealthProfile> findMine(String accountId) {
        return repository.findByAccountId(accountId);
    }

    public Terms terms(String accountId) {
        return new Terms(
                HealthConsentTerms.CURRENT_VERSION,
                HealthConsentTerms.CURRENT_TEXT,
                consents.accepted(accountId, HealthConsentTerms.CURRENT_VERSION));
    }
}
