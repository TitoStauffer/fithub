package com.fithub.account.domain.subscription;

import java.util.Optional;
import java.util.UUID;

/** Persistência das assinaturas (porta do domínio). */
public interface SubscriptionRepository {

    /** Grava a assinatura de um profissional novo, na transação do cadastro. */
    void add(Subscription subscription);

    Optional<Subscription> findByProfessional(UUID professionalId);
}
