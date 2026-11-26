package com.fithub.account.domain.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos checkouts, das assinaturas do meio de pagamento e das cobranças confirmadas (porta, DEV-0100). */
public interface PaymentRecords {

    void addCheckout(String checkoutId, UUID professionalId, boolean launch, BigDecimal value, Instant createdAt);

    Optional<CheckoutRecord> findCheckout(String checkoutId);

    /** Liga a assinatura do meio de pagamento ao checkout que a gerou; repetir não muda nada. */
    void linkSubscription(String subscriptionId, CheckoutRecord checkout, Instant now);

    Optional<SubscriptionRecord> findSubscription(String subscriptionId);

    /** Grava a cobrança confirmada; devolve {@code false} se ela já estava gravada. */
    boolean addPayment(
            String paymentId,
            SubscriptionRecord subscription,
            BigDecimal value,
            LocalDate dueDate,
            Instant confirmedAt);

    /** Quantas cobranças confirmadas a assinatura tem. */
    int countPayments(String subscriptionId);

    /** O preço de lançamento da assinatura terminou: as próximas cobranças são do preço cheio. */
    void endLaunch(String subscriptionId, Instant now);

    record CheckoutRecord(String id, UUID professionalId, boolean launch) {}

    record SubscriptionRecord(String id, UUID professionalId, boolean launch, boolean launchEnded) {}
}
