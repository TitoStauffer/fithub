package com.fithub.account.application;

import com.fithub.account.domain.payment.PaymentGateway;
import com.fithub.account.domain.payment.PaymentRecords;
import com.fithub.account.domain.payment.Pricing;
import com.fithub.account.domain.professional.ProfessionalRepository;
import com.fithub.account.domain.subscription.Subscription;
import com.fithub.account.domain.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout da assinatura mensal no Asaas para o profissional da conta autenticada (DEV-0100). O valor é o da oferta do
 * dia (preço de lançamento até a data de corte); a primeira cobrança vence no fim do teste grátis, ou hoje se ele já
 * acabou.
 * <ul>
 * <li>Com período pago valendo, ou com uma assinatura do Asaas viva, não há checkout novo: duas assinaturas cobrariam o
 * cartão todo mês (Code Review do PR #328).</li>
 * <li>Um checkout ainda aberto, que não gerou assinatura, é reaproveitado: um segundo clique ou a volta da página de
 * pagamento não criam outro. A criação é serializada por profissional: dois cliques ao mesmo tempo também não.</li>
 * </ul>
 */
@Service
public class StartCheckout {

    /** O dia da oferta e do vencimento é o de São Paulo. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** O checkout vale 1 dia no Asaas; reaproveitar até 23 h deixa folga para o profissional pagar. */
    static final Duration REUSE_WINDOW = Duration.ofHours(23);

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

        /** Já existe assinatura no Asaas, ainda não paga ou com cobrança pendente; o cartão é atualizado por lá. */
        record AlreadySubscribed() implements Result {}
    }

    @Transactional
    public Result start(String accountId) {
        Instant now = clock.instant();
        var professional = professionals.findByAccountId(accountId);
        if (professional.isEmpty()) {
            return new Result.NotProfessional();
        }
        UUID professionalId = professional.get().id();
        records.lockCheckouts(professionalId);
        Subscription subscription = subscriptions
                .findByProfessional(professionalId)
                .orElseThrow(() -> new IllegalStateException("Profissional sem assinatura."));
        if (subscription.status(now) == Subscription.Status.ACTIVE) {
            return new Result.AlreadyActive();
        }
        if (records.hasLiveSubscription(professionalId)) {
            return new Result.AlreadySubscribed();
        }
        Optional<PaymentRecords.OpenCheckout> open = records.openCheckout(professionalId, now.minus(REUSE_WINDOW));
        if (open.isPresent()) {
            return new Result.Created(
                    open.get().link(),
                    new Pricing.Offer(open.get().value(), open.get().launch()));
        }
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        LocalDate trialEnd = LocalDate.ofInstant(subscription.trialEndsAt(), ZONE);
        LocalDate firstDueDate = trialEnd.isAfter(today) ? trialEnd : today;
        Pricing.Offer offer = pricing.offerOn(today);
        PaymentGateway.Checkout checkout = gateway.createCheckout(professionalId, offer.value(), firstDueDate);
        records.addCheckout(checkout.id(), checkout.link(), professionalId, offer.launch(), offer.value(), now);
        return new Result.Created(checkout.link(), offer);
    }
}
