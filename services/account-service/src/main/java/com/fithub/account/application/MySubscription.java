package com.fithub.account.application;

import com.fithub.account.domain.payment.Pricing;
import com.fithub.account.domain.professional.ProfessionalRepository;
import com.fithub.account.domain.subscription.Subscription;
import com.fithub.account.domain.subscription.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * A assinatura do profissional da conta autenticada (DEV-0099), com o estado no instante da leitura e a oferta para quem
 * assina hoje (DEV-0102): a tela mostra o preço sem repetir a regra do {@link Pricing}.
 */
@Service
public class MySubscription {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ProfessionalRepository professionals;
    private final SubscriptionRepository subscriptions;
    private final Pricing pricing;
    private final Clock clock;

    public MySubscription(
            ProfessionalRepository professionals, SubscriptionRepository subscriptions, Pricing pricing, Clock clock) {
        this.professionals = professionals;
        this.subscriptions = subscriptions;
        this.pricing = pricing;
        this.clock = clock;
    }

    /**
     * A assinatura, o estado agora e a oferta para quem assina hoje.
     *
     * @param offer o valor da primeira cobrança e se ele é o de lançamento
     * @param launchMonths quantos meses pagos o preço de lançamento vale
     * @param regular o preço cheio, depois do lançamento
     */
    public record View(
            Subscription subscription,
            Subscription.Status status,
            Pricing.Offer offer,
            int launchMonths,
            BigDecimal regular) {}

    /** Vazio se a conta não tem cadastro de profissional. */
    public Optional<View> of(String accountId) {
        Instant now = clock.instant();
        return professionals
                .findByAccountId(accountId)
                .flatMap(professional -> subscriptions.findByProfessional(professional.id()))
                .map(subscription -> {
                    Pricing.Offer offer = pricing.offerOn(LocalDate.ofInstant(now, ZONE));
                    return new View(
                            subscription, subscription.status(now), offer, pricing.launchMonths(), pricing.monthly());
                });
    }
}
