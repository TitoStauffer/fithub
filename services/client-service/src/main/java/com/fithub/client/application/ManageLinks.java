package com.fithub.client.application;

import com.fithub.client.domain.invitation.InvalidInvitationException;
import com.fithub.client.domain.link.ConsentTerms;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkEvents;
import com.fithub.client.domain.link.LinkRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Vínculos depois do aceite (DEV-0037, FR-12): listar e encerrar, pelo cliente ou pelo profissional, e aceitar de novo
 * o termo quando ele muda de versão (DEV-0160).
 */
@Service
public class ManageLinks {

    private final LinkRepository links;
    private final LinkEvents events;
    private final TransactionOperations transaction;
    private final Clock clock;

    public ManageLinks(LinkRepository links, LinkEvents events, TransactionOperations transaction, Clock clock) {
        this.links = links;
        this.events = events;
        this.transaction = transaction;
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
        return transaction.execute(status -> {
            // Dois encerramentos ao mesmo tempo: vale o que o banco gravou primeiro, e só ele publica o
            // VinculoEncerrado (DEV-0038), na mesma transação.
            if (links.end(ended)) {
                events.ended(ended);
                return ended;
            }
            return links.findById(linkId).orElseThrow(NotFoundException::new);
        });
    }

    /** Vínculo encerrado não recebe novo aceite: o cliente faz outro vínculo, se quiser. */
    public static final class EndedLinkException extends RuntimeException {

        public EndedLinkException() {
            super("O vínculo foi encerrado.");
        }
    }

    /**
     * O cliente aceita o termo vigente num vínculo ativo aceito numa versão anterior (DEV-0160). Aceitar de novo a
     * versão que já vale não grava nada. O profissional não aceita pelo cliente (404).
     */
    public String renewConsent(String clientAccount, UUID linkId, Boolean consent, String termsVersion) {
        if (!Boolean.TRUE.equals(consent)) {
            throw new InvalidInvitationException("consent", "Para aceitar, confirme o consentimento.");
        }
        if (!ConsentTerms.isCurrent(termsVersion)) {
            throw new AcceptInvitation.TermsOutdatedException();
        }
        Link link = links.findById(linkId).orElseThrow(NotFoundException::new);
        if (link.partyOf(UUID.fromString(clientAccount))
                .filter(Link.Party.CLIENT::equals)
                .isEmpty()) {
            throw new NotFoundException();
        }
        if (link.status() != Link.Status.ACTIVE) {
            throw new EndedLinkException();
        }
        if (!links.consentVersion(linkId).map(ConsentTerms::isCurrent).orElse(false)) {
            links.addConsent(link, ConsentTerms.SCOPE, ConsentTerms.CURRENT_VERSION, clock.instant());
        }
        return ConsentTerms.CURRENT_VERSION;
    }
}
