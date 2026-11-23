package com.fithub.client.application;

import com.fithub.client.domain.link.HealthAccessAudit;
import com.fithub.client.domain.link.LinkRepository;
import com.fithub.client.domain.profile.HealthProfile;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Leitura do perfil e da saúde do cliente pelo profissional vinculado (DEV-0040, BR-02, BR-04, BR-05). Exige
 * vínculo ativo entre os dois; sem ele (ou encerrado), 404, como se o cliente não existisse.
 */
@Service
public class ReadClientAsProfessional {

    private final LinkRepository links;
    private final ManageClientProfile profiles;
    private final ManageHealthProfile health;
    private final HealthAccessAudit audit;
    private final Clock clock;

    public ReadClientAsProfessional(
            LinkRepository links,
            ManageClientProfile profiles,
            ManageHealthProfile health,
            HealthAccessAudit audit,
            Clock clock) {
        this.links = links;
        this.profiles = profiles;
        this.health = health;
        this.audit = audit;
        this.clock = clock;
    }

    /** Sem vínculo ativo, ou dado que ainda não existe (404). */
    public static final class NotFoundException extends RuntimeException {

        public NotFoundException(String message) {
            super(message);
        }
    }

    public ManageClientProfile.Result profile(String professionalAccount, UUID clientId) {
        requireActiveLink(professionalAccount, clientId);
        return profiles.findMine(clientId.toString())
                .orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
    }

    /** Cada leitura da saúde fica registrada (NFR-03) antes de o dado sair: sem registro, não há leitura. */
    public HealthProfile health(String professionalAccount, UUID clientId) {
        UUID professionalId = requireActiveLink(professionalAccount, clientId);
        HealthProfile found = health.findMine(clientId.toString())
                .orElseThrow(() -> new NotFoundException("O cliente ainda não informou os dados de saúde."));
        audit.record(professionalId, clientId, clock.instant());
        return found;
    }

    private UUID requireActiveLink(String professionalAccount, UUID clientId) {
        UUID professionalId = UUID.fromString(professionalAccount);
        if (!links.hasActiveLink(professionalId, clientId)) {
            throw new NotFoundException("Cliente não encontrado.");
        }
        return professionalId;
    }
}
