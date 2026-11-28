package com.fithub.account.domain.subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistência das assinaturas (porta do domínio). */
public interface SubscriptionRepository {

    /** Grava a assinatura de um profissional novo, na transação do cadastro. */
    void add(Subscription subscription);

    Optional<Subscription> findByProfessional(UUID professionalId);

    /**
     * Estende o período pago até {@code until} (DEV-0100); um pagamento mais antigo que chega depois nunca encurta.
     * Devolve a assinatura como ficou, ou vazio se o profissional não tem assinatura.
     */
    Optional<Subscription> extendPaidUntil(UUID professionalId, Instant until);

    /**
     * Encerra o período pago em {@code at} (estorno ou chargeback, DEV-0182), nunca depois do que já estava. Devolve a
     * assinatura como ficou só quando mudou: vazio se o período já terminava até {@code at}, ou se nada foi pago.
     */
    Optional<Subscription> endPaidAt(UUID professionalId, Instant at);
}
