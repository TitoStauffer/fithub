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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eventos do webhook do Asaas (DEV-0100). O log traz só ids do Asaas.
 * <ul>
 * <li><b>Assinatura criada:</b> liga a assinatura ao checkout do FitHub pelo {@code checkoutSession} dela, para o
 * profissional não abrir outro checkout enquanto ela vive (Code Review do PR #328).</li>
 * <li><b>Assinatura inativada ou apagada:</b> registra o fim; o profissional pode assinar de novo.</li>
 * <li><b>Cobrança confirmada:</b> conta uma vez só, pelo id dela (o Asaas entrega pelo menos uma vez), e estende o
 * período pago até o vencimento seguinte, com 1 dia de folga para a próxima cobrança confirmar. No último mês do preço
 * de lançamento, o valor passa ao cheio.</li>
 * <li><b>Cobrança estornada ou com chargeback (DEV-0182, decisão do Tito em 27/11/2026):</b> o período pago termina no
 * momento do evento, nunca depois do que já estava. Conta uma vez só por cobrança, e a cobrança desconhecida é
 * ignorada com aviso. O que já foi entregue ao cliente continua com ele (BR-14).</li>
 * <li>Um evento sem os campos de que precisa, ou de assinatura sem checkout do FitHub, é ignorado com aviso: um erro
 * faria o Asaas reentregar sem fim.</li>
 * </ul>
 */
@Service
public class HandlePaymentEvent {

    /** Cobrança confirmada: no cartão, o CONFIRMED chega primeiro e o RECEIVED cerca de 32 dias depois. */
    static final Set<String> CONFIRMED = Set.of("PAYMENT_CONFIRMED", "PAYMENT_RECEIVED");

    /** Cobrança estornada ou contestada pelo pagador (chargeback). */
    static final Set<String> REFUNDED =
            Set.of("PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE");

    static final String SUBSCRIPTION_CREATED = "SUBSCRIPTION_CREATED";

    /** A assinatura deixou de existir no Asaas. */
    static final Set<String> SUBSCRIPTION_ENDED = Set.of("SUBSCRIPTION_INACTIVATED", "SUBSCRIPTION_DELETED");

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

    /**
     * Processa o evento; uma falha desfaz tudo, e o Asaas entrega de novo.
     *
     * @param subscriptionId a assinatura dos eventos de assinatura; nulo nos de cobrança
     */
    @Transactional
    public void handle(String type, Payment payment, String subscriptionId) {
        Instant now = clock.instant();
        if (subscriptionId != null) {
            handleSubscription(type, subscriptionId, now);
        }
        if (CONFIRMED.contains(type) && payment != null) {
            handlePayment(payment, now);
        }
        if (REFUNDED.contains(type) && payment != null) {
            handleRefund(payment, now);
        }
    }

    /** Estorno ou chargeback: na primeira vez da cobrança, o período pago termina agora. */
    private void handleRefund(Payment payment, Instant now) {
        if (payment.id() == null) {
            log.warn("Estorno sem id de cobrança; ignorado");
            return;
        }
        Optional<UUID> professional = records.refundPayment(payment.id(), now);
        if (professional.isEmpty()) {
            if (!records.hasPayment(payment.id())) {
                log.warn("Estorno da cobrança {}, que não foi registrada; ignorado", payment.id());
            }
            return;
        }
        subscriptions.endPaidAt(professional.get(), now).ifPresent(changed -> events.changed(changed, now));
        log.info("Cobrança {} estornada; o período pago terminou", payment.id());
    }

    /** Assinatura criada: liga ao checkout. Inativada ou apagada: registra o fim. */
    private void handleSubscription(String type, String subscriptionId, Instant now) {
        if (SUBSCRIPTION_CREATED.equals(type)
                && subscriptionOf(subscriptionId, now).isEmpty()) {
            log.warn("Assinatura {} sem checkout do FitHub; ignorada", subscriptionId);
        }
        if (SUBSCRIPTION_ENDED.contains(type)) {
            records.endSubscription(subscriptionId, now);
        }
    }

    /** Cobrança confirmada: grava uma vez e estende o período pago. */
    private void handlePayment(Payment payment, Instant now) {
        if (payment.id() == null
                || payment.subscriptionId() == null
                || payment.value() == null
                || payment.dueDate() == null) {
            log.warn("Cobrança {} sem id, assinatura, valor ou vencimento; ignorada", payment.id());
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

    /**
     * A assinatura já ligada ou a que o checkout do FitHub gerou ({@code checkoutSession}). Outra assinatura para um
     * checkout já ligado fica sem ligação.
     */
    private Optional<PaymentRecords.SubscriptionRecord> subscriptionOf(String subscriptionId, Instant now) {
        Optional<PaymentRecords.SubscriptionRecord> known = records.findSubscription(subscriptionId);
        if (known.isPresent()) {
            return known;
        }
        return gateway.checkoutOf(subscriptionId).flatMap(records::findCheckout).flatMap(checkout -> {
            records.linkSubscription(subscriptionId, checkout, now);
            return records.findSubscription(subscriptionId);
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
