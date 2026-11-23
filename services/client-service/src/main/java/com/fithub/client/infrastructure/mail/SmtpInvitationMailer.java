package com.fithub.client.infrastructure.mail;

import com.fithub.client.domain.invitation.InvitationMailer;
import com.fithub.client.domain.professional.Professional;
import com.fithub.client.domain.professional.ProfessionalType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Convite por e-mail pelo SMTP configurado (DEV-0035; Mailpit no compose). O texto traz o nome do profissional,
 * o link e o código; nenhum dado do cliente além do endereço. Falha de envio vira {@code false} e um log sem o
 * endereço, o link ou o código.
 */
@Component
class SmtpInvitationMailer implements InvitationMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpInvitationMailer.class);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"));

    private final JavaMailSender sender;
    private final String from;

    SmtpInvitationMailer(
            JavaMailSender sender, @Value("${fithub.mail.from:FitHub <nao-responda@fithub.local>}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public boolean send(String email, Professional professional, String link, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("%s convidou você para o FitHub".formatted(professional.name()));
        message.setText("""
                Olá!

                %s, %s, convidou você para acompanhar %s pelo FitHub.

                Para aceitar, abra o link no celular com o app FitHub instalado:
                %s

                Ou informe este código no app: %s

                O convite vale até %s e só pode ser usado uma vez. Na hora de aceitar, o app mostra o que será
                compartilhado com o profissional.

                Se você não esperava este convite, ignore este e-mail.
                """.formatted(
                        professional.name(),
                        label(professional.type()),
                        professional.type() == ProfessionalType.NUTRITIONIST ? "sua alimentação" : "seus treinos",
                        link,
                        code,
                        DATE.format(expiresAt)));
        try {
            sender.send(message);
            return true;
        } catch (MailException failure) {
            // Sem o endereço, o link ou o código no log: só o tipo da falha.
            log.warn(
                    "Falha ao enviar o e-mail do convite: {}",
                    failure.getClass().getSimpleName());
            return false;
        }
    }

    private static String label(ProfessionalType type) {
        return type == ProfessionalType.NUTRITIONIST ? "nutricionista" : "personal trainer";
    }
}
