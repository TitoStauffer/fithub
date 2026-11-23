package com.fithub.client.application;

import com.fithub.client.domain.invitation.Invitation;
import com.fithub.client.domain.invitation.InvitationMailer;
import com.fithub.client.domain.invitation.InvitationRepository;
import com.fithub.client.domain.invitation.InviteSecrets;
import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.ConsentTerms;
import com.fithub.client.domain.professional.Professional;
import com.fithub.client.domain.professional.ProfessionalDirectory;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.commons.ids.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Convites do profissional ao cliente (DEV-0035, FR-10): criar (por e-mail ou para compartilhar), listar,
 * cancelar e consultar pelo link ou pelo código, para a tela de aceite.
 */
@Service
public class ManageInvitations {

    private static final int MAX_ATTEMPTS = 3;

    private final InvitationRepository invitations;
    private final ProfessionalDirectory professionals;
    private final InvitationMailer mailer;
    private final Clock clock;
    private final String linkBase;

    public ManageInvitations(
            InvitationRepository invitations,
            ProfessionalDirectory professionals,
            InvitationMailer mailer,
            Clock clock,
            @Value("${fithub.invitations.link-base:fithub://convite/}") String linkBase) {
        this.invitations = invitations;
        this.professionals = professionals;
        this.mailer = mailer;
        this.clock = clock;
        this.linkBase = linkBase;
    }

    /** Convite criado, com o link e o código em claro: a única vez que eles existem fora do e-mail. */
    public record Created(Invitation invitation, String link, String code, boolean emailSent) {}

    /** O que a tela de aceite mostra: quem convida e o que será compartilhado (BR-04). */
    public record Preview(
            Kind kind,
            String professionalName,
            ProfessionalType professionalType,
            Set<SharedData> sharedData,
            Instant expiresAt) {

        public enum Kind {
            INVITATION,
            PROFESSIONAL_CODE
        }
    }

    /** Convite vencido, já usado ou cancelado (410). */
    public static final class GoneException extends RuntimeException {

        public GoneException() {
            super("Este convite não vale mais. Peça um novo ao profissional.");
        }
    }

    /** Convite ou código que não existe, ou convite de outro profissional (404). */
    public static final class NotFoundException extends RuntimeException {

        public NotFoundException() {
            super("Convite não encontrado.");
        }
    }

    public Created create(String professionalAccount, String email) {
        Professional professional = professionalOf(professionalAccount);
        Instant now = clock.instant();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String token = InviteSecrets.newToken();
            String code = InviteSecrets.newCode();
            Invitation invitation = Invitation.create(
                    UuidV7.next(clock),
                    professional.id(),
                    email,
                    InviteSecrets.hash(token),
                    InviteSecrets.hash(code),
                    now);
            if (invitations.saveNew(invitation)) {
                String link = linkBase + token;
                // O e-mail sai depois da gravação: se falhar, o convite vale e o profissional compartilha o link.
                boolean sent = invitation.email() != null
                        && mailer.send(invitation.email(), professional, link, code, invitation.expiresAt());
                return new Created(invitation, link, code, sent);
            }
        }
        throw new IllegalStateException("Não foi possível gerar segredos únicos para o convite.");
    }

    public List<Invitation> listMine(String professionalAccount) {
        return invitations.listByProfessional(
                professionalOf(professionalAccount).id());
    }

    /** Cancela um convite do próprio profissional. Convite de outro profissional é 404, como se não existisse. */
    public Invitation cancel(String professionalAccount, UUID invitationId) {
        UUID professionalId = professionalOf(professionalAccount).id();
        Invitation invitation = invitations
                .findById(invitationId)
                .filter(found -> found.professionalId().equals(professionalId))
                .orElseThrow(NotFoundException::new);
        Invitation cancelled = invitation.cancel(clock.instant());
        if (cancelled != invitation) {
            invitations.updateStatus(cancelled);
        }
        return cancelled;
    }

    /** Consulta pelo token do link. */
    public Preview previewByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new NotFoundException();
        }
        return previewOf(invitations.findByTokenHash(InviteSecrets.hash(token)).orElseThrow(NotFoundException::new));
    }

    /** Consulta pelo código: primeiro o código de um convite, depois o código pessoal de um profissional. */
    public Preview previewByCode(String rawCode) {
        String code = InviteSecrets.normalizeCode(rawCode).orElseThrow(NotFoundException::new);
        Optional<Invitation> invitation = invitations.findByCodeHash(InviteSecrets.hash(code));
        if (invitation.isPresent()) {
            return previewOf(invitation.get());
        }
        Professional professional = professionals.findByInviteCode(code).orElseThrow(NotFoundException::new);
        return new Preview(
                Preview.Kind.PROFESSIONAL_CODE, professional.name(), professional.type(), ConsentTerms.SCOPE, null);
    }

    private Preview previewOf(Invitation invitation) {
        if (!invitation.usableAt(clock.instant())) {
            throw new GoneException();
        }
        Professional professional =
                professionals.findById(invitation.professionalId()).orElseThrow(NotFoundException::new);
        return new Preview(
                Preview.Kind.INVITATION,
                professional.name(),
                professional.type(),
                ConsentTerms.SCOPE,
                invitation.expiresAt());
    }

    private Professional professionalOf(String account) {
        return professionals.findById(UUID.fromString(account)).orElseThrow(ProfessionalNotReadyException::new);
    }
}
