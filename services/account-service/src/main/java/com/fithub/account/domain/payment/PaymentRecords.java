package com.fithub.account.domain.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos checkouts, das assinaturas do meio de pagamento e das cobranças confirmadas (porta, DEV-0100). */
public interface PaymentRecords {

    void addCheckout(
            String checkoutId, String link, UUID professionalId, boolean launch, BigDecimal value, Instant createdAt);

    Optional<CheckoutRecord> findCheckout(String checkoutId);

    /** O checkout criado depois de {@code createdAfter} que ainda não gerou assinatura, para reaproveitar o link. */
    Optional<OpenCheckout> openCheckout(UUID professionalId, Instant createdAfter);

    /**
     * Trava, até o fim da transação, a criação de checkout do profissional: dois cliques ao mesmo tempo não criam dois
     * checkouts (Code Review do PR #328).
     */
    void lockCheckouts(UUID professionalId);

    /** Se o profissional tem assinatura do meio de pagamento ligada e ainda não terminada. */
    boolean hasLiveSubscription(UUID professionalId);

    /**
     * Liga a assinatura do meio de pagamento ao checkout que a gerou; repetir não muda nada. Outra assinatura para o
     * mesmo checkout não é ligada.
     */
    void linkSubscription(String subscriptionId, CheckoutRecord checkout, Instant now);

    Optional<SubscriptionRecord> findSubscription(String subscriptionId);

    /** A assinatura do meio de pagamento foi inativada ou apagada lá: o profissional pode abrir outro checkout. */
    void endSubscription(String subscriptionId, Instant now);

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

    record OpenCheckout(String id, String link, BigDecimal value, boolean launch) {}

    record SubscriptionRecord(String id, UUID professionalId, boolean launch, boolean launchEnded) {}
}
