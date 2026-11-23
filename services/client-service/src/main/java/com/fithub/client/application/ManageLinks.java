package com.fithub.client.application;

import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Vínculos depois do aceite (DEV-0037, FR-12): listar e encerrar, pelo cliente ou pelo profissional. */
@Service
public class ManageLinks {

    private final LinkRepository links;
    private final Clock clock;

    public ManageLinks(LinkRepository links, Clock clock) {
        this.links = links;
        this.clock = clock;
    }

    /** Vínculo que não existe ou do qual a conta não é parte (404: não revela que existe). */
    public static final class NotFoundException extends RuntimeException {

        public NotFoundException() {
            super("Vínculo não encontrado.");
        }
    }

    public List<LinkRepository.WithProfessional> listMine(String clientAccount) {
        return links.listByClient(UUID.fromString(clientAccount));
    }

    public List<LinkRepository.ActiveClient> listClients(String professionalAccount) {
        return links.listActiveByProfessional(UUID.fromString(professionalAccount));
    }

    /**
     * Encerra o vínculo pela parte que a conta é nele (cliente ou profissional). Encerrar de novo devolve o
     * encerramento que valeu, sem mudar nada.
     */
    public Link end(String account, UUID linkId) {
        Link link = links.findById(linkId).orElseThrow(NotFoundException::new);
        Link.Party party = link.partyOf(UUID.fromString(account)).orElseThrow(NotFoundException::new);
        Link ended = link.end(party, clock.instant());
        if (ended == link) {
            return link;
        }
        // Dois encerramentos ao mesmo tempo: vale o que o banco gravou primeiro.
        return links.end(ended) ? ended : links.findById(linkId).orElseThrow(NotFoundException::new);
    }
}
