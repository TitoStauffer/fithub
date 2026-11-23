package com.fithub.client.application;

import com.fithub.client.domain.professional.Professional;
import com.fithub.client.domain.professional.ProfessionalDirectory;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Código pessoal do profissional (DEV-0035): consultar e trocar. O cliente o informa para pedir o vínculo. */
@Service
public class ManageInviteCode {

    private final ProfessionalDirectory professionals;
    private final Clock clock;

    public ManageInviteCode(ProfessionalDirectory professionals, Clock clock) {
        this.professionals = professionals;
        this.clock = clock;
    }

    public String mine(String professionalAccount) {
        return professionalOf(professionalAccount).inviteCode();
    }

    /** Troca o código; o antigo deixa de valer na hora. */
    public String rotate(String professionalAccount) {
        return professionals.rotateInviteCode(
                professionalOf(professionalAccount).id(), clock.instant());
    }

    private Professional professionalOf(String account) {
        return professionals.findById(UUID.fromString(account)).orElseThrow(ProfessionalNotReadyException::new);
    }
}
