package com.fithub.account.application;

import com.fithub.account.domain.payment.PaymentGateway;
import com.fithub.account.domain.payment.PaymentRecords;
import com.fithub.account.domain.payment.Pricing;
import com.fithub.account.domain.subscription.SubscriptionEvents;
import com.fithub.account.domain.subscription.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eventos do webhook do Asaas (DEV-0100). O Asaas entrega pelo menos uma vez: a cobrança conta uma vez só, pelo id dela. A cobrança
 * confirmada de uma assinatura grava o pagamento e estende o período pago até o vencimento seguinte, com 1 dia de
 * folga para a próxima cobrança confirmar; no último mês do preço de lançamento, o valor passa ao cheio. Os demais
 * eventos são aceitos e ignorados. O log traz só ids do Asaas.
 */
@Service
public class HandlePaymentEvent {

    /** Cobrança confirmada: no cartão, o CONFIRMED chega primeiro e o RECEIVED cerca de 32 dias depois. */
    static final Set<String> CONFIRMED = Set.of("PAYMENT_CONFIRMED", "PAYMENT_RECEIVED");

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentEvent.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final PaymentRecords records;
    private final PaymentGateway gateway;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionEvents events;
    private final Pricing pricing;
    private final Clock clock;

    public HandlePaymentEvent(
            PaymentRecords records,
            PaymentGateway gateway,
            SubscriptionRepository subscriptions,
            SubscriptionEvents events,
            Pricing pricing,
            Clock clock) {
        this.records = records;
        this.gateway = gateway;
        this.subscriptions = subscriptions;
        this.events = events;
        this.pricing = pricing;
        this.clock = clock;
    }

    /** A cobrança do evento, quando ele traz uma. */
    public record Payment(String id, String subscriptionId, BigDecimal value, LocalDate dueDate) {}

    /** Processa o evento; uma falha desfaz tudo, e o Asaas entrega de novo. */
    @Transactional
    public void handle(String type, Payment payment) {
        Instant now = clock.instant();
        if (!CONFIRMED.contains(type) || payment == null || payment.subscriptionId() == null) {
            return;
        }
        Optional<PaymentRecords.SubscriptionRecord> subscription = subscriptionOf(payment.subscriptionId(), now);
        if (subscription.isEmpty()) {
            log.warn(
                    "Cobrança {} de uma assinatura {} sem checkout do FitHub; ignorada",
                    payment.id(),
                    payment.subscriptionId());
            return;
        }
        if (!records.addPayment(payment.id(), subscription.get(), payment.value(), payment.dueDate(), now)) {
            return;
        }
        Instant paidUntil =
                payment.dueDate().plusMonths(1).plusDays(1).atStartOfDay(ZONE).toInstant();
        subscriptions
                .extendPaidUntil(subscription.get().professionalId(), paidUntil)
                .ifPresent(changed -> events.changed(changed, now));
        endLaunchAfterLastMonth(subscription.get(), now);
    }

    /** A assinatura já ligada ou, no primeiro pagamento, a que o checkout do FitHub gerou (checkoutSession). */
    private Optional<PaymentRecords.SubscriptionRecord> subscriptionOf(String subscriptionId, Instant now) {
        Optional<PaymentRecords.SubscriptionRecord> known = records.findSubscription(subscriptionId);
        if (known.isPresent()) {
            return known;
        }
        return gateway.checkoutOf(subscriptionId).flatMap(records::findCheckout).map(checkout -> {
            records.linkSubscription(subscriptionId, checkout, now);
            return records.findSubscription(subscriptionId).orElseThrow();
        });
    }

    private void endLaunchAfterLastMonth(PaymentRecords.SubscriptionRecord subscription, Instant now) {
        if (!subscription.launch() || subscription.launchEnded()) {
            return;
        }
        if (records.countPayments(subscription.id()) >= pricing.launchMonths()) {
            gateway.changeValue(subscription.id(), pricing.monthly());
            records.endLaunch(subscription.id(), now);
            log.info("Preço de lançamento da assinatura {} terminou", subscription.id());
        }
    }
}
