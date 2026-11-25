package com.fithub.training.domain.access;

/** O profissional está sem assinatura válida: não cria nem edita prescrições, e o que já entregou continua (BR-14). */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException() {
        super("Sua assinatura venceu. Renove para criar ou editar prescrições.");
    }
}
