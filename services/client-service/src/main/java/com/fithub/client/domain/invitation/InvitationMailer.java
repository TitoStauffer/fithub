package com.fithub.client.domain.invitation;

import com.fithub.client.domain.professional.Professional;
import java.time.Instant;

/** Envio do convite por e-mail (porta do domínio). */
public interface InvitationMailer {

    /**
     * Envia o convite com o link e o código. Devolve {@code false} se o envio falhar: o convite continua
     * valendo e o profissional pode compartilhar o link. A mensagem não traz dado do cliente além do e-mail
     * para onde vai.
     */
    boolean send(String email, Professional professional, String link, String code, Instant expiresAt);
}
