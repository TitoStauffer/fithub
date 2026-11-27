package com.fithub.account.infrastructure.persistence;

import com.fithub.account.domain.subscription.Subscription;
import com.fithub.account.domain.subscription.SubscriptionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Assinaturas no Postgres do account-service (migration V5). */
@Repository
class JdbcSubscriptionRepository implements SubscriptionRepository {

    private final JdbcClient jdbc;

    JdbcSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Subscription subscription) {
        jdbc.sql("""
                        insert into subscription (professional_id, plan, trial_ends_at, paid_until, created_at)
                        values (:professionalId, :plan, :trialEndsAt, :paidUntil, :createdAt)
                        """)
                .param("professionalId", subscription.professionalId())
                .param("plan", subscription.plan().name())
                .param("trialEndsAt", Timestamp.from(subscription.trialEndsAt()))
                .param("paidUntil", subscription.paidUntil() == null ? null : Timestamp.from(subscription.paidUntil()))
                .param("createdAt", Timestamp.from(subscription.createdAt()))
                .update();
    }

    @Override
    public Optional<Subscription> findByProfessional(UUID professionalId) {
        return jdbc.sql("""
                        select professional_id, plan, trial_ends_at, paid_until, created_at
                          from subscription
                         where professional_id = :professionalId
                        """)
                .param("professionalId", professionalId)
                .query(JdbcSubscriptionRepository::map)
                .optional();
    }

    @Override
    public Optional<Subscription> extendPaidUntil(UUID professionalId, Instant until) {
        return jdbc.sql("""
                        update subscription
                           set paid_until = greatest(coalesce(paid_until, :until), :until)
                         where professional_id = :professionalId
                        returning professional_id, plan, trial_ends_at, paid_until, created_at
                        """)
                .param("professionalId", professionalId)
                .param("until", Timestamp.from(until))
                .query(JdbcSubscriptionRepository::map)
                .optional();
    }

    private static Subscription map(ResultSet row, int index) throws SQLException {
        Timestamp paidUntil = row.getTimestamp("paid_until");
        return new Subscription(
                row.getObject("professional_id", UUID.class),
                Subscription.Plan.valueOf(row.getString("plan")),
                row.getTimestamp("trial_ends_at").toInstant(),
                paidUntil == null ? null : paidUntil.toInstant(),
                row.getTimestamp("created_at").toInstant());
    }
}
