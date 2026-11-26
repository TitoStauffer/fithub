package com.fithub.account.infrastructure.persistence;

import com.fithub.account.domain.payment.PaymentRecords;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Checkouts, assinaturas do Asaas, cobranças confirmadas no Postgres (migration V7). */
@Repository
class JdbcPaymentRecords implements PaymentRecords {

    private final JdbcClient jdbc;

    JdbcPaymentRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void addCheckout(
            String checkoutId, UUID professionalId, boolean launch, BigDecimal value, Instant createdAt) {
        jdbc.sql("insert into payment_checkout (id, professional_id, launch, value, created_at) values (?, ?, ?, ?, ?)")
                .params(checkoutId, professionalId, launch, value, Timestamp.from(createdAt))
                .update();
    }

    @Override
    public Optional<CheckoutRecord> findCheckout(String checkoutId) {
        return jdbc.sql("select id, professional_id, launch from payment_checkout where id = ?")
                .param(checkoutId)
                .query((row, index) -> new CheckoutRecord(
                        row.getString("id"), row.getObject("professional_id", UUID.class), row.getBoolean("launch")))
                .optional();
    }

    @Override
    public void linkSubscription(String subscriptionId, CheckoutRecord checkout, Instant now) {
        jdbc.sql("insert into payment_subscription (id, checkout_id, professional_id, created_at) values (?, ?, ?, ?)"
                        + " on conflict do nothing")
                .params(subscriptionId, checkout.id(), checkout.professionalId(), Timestamp.from(now))
                .update();
    }

    @Override
    public Optional<SubscriptionRecord> findSubscription(String subscriptionId) {
        return jdbc.sql("select s.id, s.professional_id, c.launch, s.launch_ended_at is not null as launch_ended"
                        + " from payment_subscription s join payment_checkout c on c.id = s.checkout_id where s.id = ?")
                .param(subscriptionId)
                .query((row, index) -> new SubscriptionRecord(
                        row.getString("id"),
                        row.getObject("professional_id", UUID.class),
                        row.getBoolean("launch"),
                        row.getBoolean("launch_ended")))
                .optional();
    }

    @Override
    public boolean addPayment(
            String paymentId,
            SubscriptionRecord subscription,
            BigDecimal value,
            LocalDate dueDate,
            Instant confirmedAt) {
        return jdbc.sql(
                                "insert into subscription_payment (payment_id, subscription_id, professional_id, value, due_date,"
                                        + " confirmed_at) values (?, ?, ?, ?, ?, ?) on conflict do nothing")
                        .params(
                                paymentId,
                                subscription.id(),
                                subscription.professionalId(),
                                value,
                                dueDate,
                                Timestamp.from(confirmedAt))
                        .update()
                == 1;
    }

    @Override
    public int countPayments(String subscriptionId) {
        return jdbc.sql("select count(*) from subscription_payment where subscription_id = ?")
                .param(subscriptionId)
                .query(Integer.class)
                .single();
    }

    @Override
    public void endLaunch(String subscriptionId, Instant now) {
        jdbc.sql("update payment_subscription set launch_ended_at = ? where id = ? and launch_ended_at is null")
                .params(Timestamp.from(now), subscriptionId)
                .update();
    }
}
