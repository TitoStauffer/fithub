package com.fithub.account.application;

import com.fithub.account.domain.payment.PaymentGateway;
import com.fithub.account.domain.payment.PaymentRecords;
import com.fithub.account.domain.payment.Pricing;
import com.fithub.account.domain.professional.ProfessionalRepository;
import com.fithub.account.domain.subscription.Subscription;
import com.fithub.account.domain.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Checkout da assinatura mensal no Asaas para o profissional da conta autenticada (DEV-0100). O valor é o da oferta do
 * dia (preço de lançamento até a data de corte); a primeira cobrança vence no fim do teste grátis, ou hoje se ele já
 * acabou. A conta só assina sem período pago valendo.
 */
@Service
public class StartCheckout {

    /** O dia da oferta e do vencimento é o de São Paulo. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ProfessionalRepository professionals;
    private final SubscriptionRepository subscriptions;
    private final PaymentGateway gateway;
    private final PaymentRecords records;
    private final Pricing pricing;
    private final Clock clock;

    public StartCheckout(
            ProfessionalRepository professionals,
            SubscriptionRepository subscriptions,
            PaymentGateway gateway,
            PaymentRecords records,
            Pricing pricing,
            Clock clock) {
        this.professionals = professionals;
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.records = records;
        this.pricing = pricing;
        this.clock = clock;
    }

    /** O resultado: o link do checkout, ou o motivo de não haver um. */
    public sealed interface Result {
        record Created(String link, Pricing.Offer offer) implements Result {}

        record NotProfessional() implements Result {}

        record AlreadyActive() implements Result {}
    }

    public Result start(String accountId) {
        Instant now = clock.instant();
        var professional = professionals.findByAccountId(accountId);
        if (professional.isEmpty()) {
            return new Result.NotProfessional();
        }
        Subscription subscription = subscriptions
                .findByProfessional(professional.get().id())
                .orElseThrow(() -> new IllegalStateException("Profissional sem assinatura."));
        if (subscription.status(now) == Subscription.Status.ACTIVE) {
            return new Result.AlreadyActive();
        }
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        LocalDate trialEnd = LocalDate.ofInstant(subscription.trialEndsAt(), ZONE);
        LocalDate firstDueDate = trialEnd.isAfter(today) ? trialEnd : today;
        Pricing.Offer offer = pricing.offerOn(today);
        PaymentGateway.Checkout checkout =
                gateway.createCheckout(professional.get().id(), offer.value(), firstDueDate);
        records.addCheckout(checkout.id(), professional.get().id(), offer.launch(), offer.value(), now);
        return new Result.Created(checkout.link(), offer);
    }
}
