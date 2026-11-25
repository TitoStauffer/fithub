package com.fithub.account.application;

import com.fithub.account.domain.professional.ProfessionalRepository;
import com.fithub.account.domain.subscription.Subscription;
import com.fithub.account.domain.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** A assinatura do profissional da conta autenticada (DEV-0099), com o estado no instante da leitura. */
@Service
public class MySubscription {

    private final ProfessionalRepository professionals;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public MySubscription(ProfessionalRepository professionals, SubscriptionRepository subscriptions, Clock clock) {
        this.professionals = professionals;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    /** A assinatura e o estado agora. */
    public record View(Subscription subscription, Subscription.Status status) {}

    /** Vazio se a conta não tem cadastro de profissional. */
    public Optional<View> of(String accountId) {
        Instant now = clock.instant();
        return professionals
                .findByAccountId(accountId)
                .flatMap(professional -> subscriptions.findByProfessional(professional.id()))
                .map(subscription -> new View(subscription, subscription.status(now)));
    }
}
