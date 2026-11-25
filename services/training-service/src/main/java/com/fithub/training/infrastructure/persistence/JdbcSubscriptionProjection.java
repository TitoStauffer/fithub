package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.access.SubscriptionProjection;
import com.fithub.training.domain.access.SubscriptionState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Projeção das assinaturas (migration V15__assinatura_do_profissional). */
@Repository
class JdbcSubscriptionProjection implements SubscriptionProjection {

    private final JdbcClient jdbc;

    JdbcSubscriptionProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void update(UUID professionalId, SubscriptionState state, Instant occurredAt) {
        // O mais novo vence: evento atrasado não volta a assinatura para trás.
        jdbc.sql("""
                        insert into professional_subscription (professional_id, active, valid_until, occurred_at)
                        values (:id, :active, :validUntil, :occurredAt)
                        on conflict (professional_id) do update
                           set active = excluded.active, valid_until = excluded.valid_until,
                               occurred_at = excluded.occurred_at
                         where professional_subscription.occurred_at < excluded.occurred_at
                        """)
                .param("id", professionalId)
                .param("active", state.active())
                .param("validUntil", state.validUntil() == null ? null : Timestamp.from(state.validUntil()))
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public Optional<SubscriptionState> find(UUID professionalId) {
        return jdbc.sql("select active, valid_until from professional_subscription where professional_id = :id")
                .param("id", professionalId)
                .query((row, index) -> {
                    Timestamp validUntil = row.getTimestamp("valid_until");
                    return new SubscriptionState(
                            row.getBoolean("active"), validUntil == null ? null : validUntil.toInstant());
                })
                .optional();
    }
}
