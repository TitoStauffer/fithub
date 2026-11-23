package com.fithub.client.application;

import com.fithub.client.domain.invitation.InvalidInvitationException;
import com.fithub.client.domain.invitation.Invitation;
import com.fithub.client.domain.invitation.InvitationRepository;
import com.fithub.client.domain.invitation.InviteSecrets;
import com.fithub.client.domain.link.ConsentTerms;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkRepository;
import com.fithub.client.domain.link.LinkRuleException;
import com.fithub.client.domain.professional.Professional;
import com.fithub.client.domain.professional.ProfessionalDirectory;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.client.domain.profile.ClientProfileRepository;
import com.fithub.client.domain.profile.ProfileRequiredException;
import com.fithub.commons.ids.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Aceite do convite pelo cliente (DEV-0036, FR-11, FR-13): pelo token do link, pelo código do convite ou pelo
 * código pessoal do profissional. Exige o consentimento explícito na versão do termo que a tela mostrou, e grava
 * vínculo e consentimento na mesma transação.
 */
@Service
public class AcceptInvitation {

    private final InvitationRepository invitations;
    private final ProfessionalDirectory professionals;
    private final ClientProfileRepository profiles;
    private final LinkRepository links;
    private final TransactionOperations transaction;
    private final Clock clock;

    public AcceptInvitation(
            InvitationRepository invitations,
            ProfessionalDirectory professionals,
            ClientProfileRepository profiles,
            LinkRepository links,
            TransactionOperations transaction,
            Clock clock) {
        this.invitations = invitations;
        this.professionals = professionals;
        this.profiles = profiles;
        this.links = links;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** O que o cliente enviou. {@code clientAccount} vem sempre do token. */
    public record Command(String clientAccount, String token, String code, Boolean consent, String termsVersion) {}

    public record Result(Link link, Professional professional) {}

    /** O termo mudou depois que a tela o mostrou: o cliente precisa ler a versão nova. */
    public static final class TermsOutdatedException extends RuntimeException {

        public TermsOutdatedException() {
            super("O termo de consentimento mudou. Leia a versão atual antes de aceitar.");
        }
    }

    public Result accept(Command command) {
        if (!Boolean.TRUE.equals(command.consent())) {
            throw new InvalidInvitationException("consent", "Para aceitar, confirme o consentimento.");
        }
        if (!ConsentTerms.isCurrent(command.termsVersion())) {
            throw new TermsOutdatedException();
        }
        if (profiles.findByAccountId(command.clientAccount()).isEmpty()) {
            throw new ProfileRequiredException("Crie seu perfil antes de aceitar um convite.");
        }
        UUID clientId = UUID.fromString(command.clientAccount());
        Target target = targetOf(command);
        Professional professional = target.professional();
        Instant now = clock.instant();
        Link link = Link.start(
                UuidV7.next(clock),
                clientId,
                professional.id(),
                professional.type(),
                target.invitation().map(Invitation::id).orElse(null),
                now);

        transaction.executeWithoutResult(status -> {
            links.findActive(clientId, professional.type()).ifPresent(active -> {
                throw activeLinkRule(active.professionalId().equals(professional.id()), professional.type());
            });
            // Uso único: o convite só vira aceito se ainda estiver pendente e válido neste instante.
            if (target.invitation().isPresent()
                    && !invitations.markAccepted(target.invitation().get().id(), now)) {
                throw new ManageInvitations.GoneException();
            }
            try {
                links.saveWithConsent(link, ConsentTerms.SCOPE, ConsentTerms.CURRENT_VERSION, now);
            } catch (LinkRepository.ActiveLinkConflict raced) {
                // Outro aceite do mesmo tipo ganhou a corrida; a transação inteira (inclusive o convite) volta.
                throw activeLinkRule(false, professional.type());
            }
        });
        return new Result(link, professional);
    }

    private record Target(Professional professional, Optional<Invitation> invitation) {}

    private Target targetOf(Command command) {
        Optional<Invitation> invitation;
        if (command.token() != null && !command.token().isBlank()) {
            invitation = invitations.findByTokenHash(InviteSecrets.hash(command.token()));
            if (invitation.isEmpty()) {
                throw new ManageInvitations.NotFoundException();
            }
        } else {
            String code =
                    InviteSecrets.normalizeCode(command.code()).orElseThrow(ManageInvitations.NotFoundException::new);
            invitation = invitations.findByCodeHash(InviteSecrets.hash(code));
            if (invitation.isEmpty()) {
                // Código pessoal do profissional: vínculo sem convite.
                Professional professional =
                        professionals.findByInviteCode(code).orElseThrow(ManageInvitations.NotFoundException::new);
                return new Target(professional, Optional.empty());
            }
        }
        if (!invitation.get().usableAt(clock.instant())) {
            throw new ManageInvitations.GoneException();
        }
        Professional professional = professionals
                .findById(invitation.get().professionalId())
                .orElseThrow(ManageInvitations.NotFoundException::new);
        return new Target(professional, invitation);
    }

    private static LinkRuleException activeLinkRule(boolean sameProfessional, ProfessionalType type) {
        if (sameProfessional) {
            return new LinkRuleException("Você já está vinculado a este profissional.");
        }
        return new LinkRuleException(
                type == ProfessionalType.NUTRITIONIST
                        ? "Você já tem um nutricionista ativo. Encerre o vínculo atual antes de aceitar outro."
                        : "Você já tem um personal ativo. Encerre o vínculo atual antes de aceitar outro.");
    }
}
