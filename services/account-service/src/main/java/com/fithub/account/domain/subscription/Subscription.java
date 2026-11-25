package com.fithub.account.domain.subscription;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Assinatura do profissional (DEV-0099, FR-05, FR-06). O estado não é guardado: sai das datas na hora da leitura,
 * sem rotina agendada para virar o estado quando o teste ou o período pago acaba.
 *
 * @param trialEndsAt fim do teste grátis, que começa no cadastro
 * @param paidUntil até quando o período pago vale; vazio enquanto nada foi pago
 */
public record Subscription(UUID professionalId, Plan plan, Instant trialEndsAt, Instant paidUntil, Instant createdAt) {

    /** Os planos. No MVP há um só; o preço e o meio de pagamento são da DEV-0098. */
    public enum Plan {
        PROFESSIONAL_MONTHLY
    }

    /** O estado da assinatura num instante. */
    public enum Status {
        TRIAL,
        ACTIVE,
        EXPIRED
    }

    public Subscription {
        Objects.requireNonNull(professionalId, "professionalId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(trialEndsAt, "trialEndsAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A assinatura de um profissional recém-cadastrado: em teste por {@code trial} a partir de agora. */
    public static Subscription startTrial(UUID professionalId, Instant now, Duration trial) {
        if (trial.isNegative() || trial.isZero()) {
            throw new IllegalArgumentException("O teste grátis precisa ter duração.");
        }
        return new Subscription(professionalId, Plan.PROFESSIONAL_MONTHLY, now.plus(trial), null, now);
    }

    /** Pago no futuro é ativa; senão, teste ainda correndo é teste; senão, vencida. */
    public Status status(Instant now) {
        if (paidUntil != null && now.isBefore(paidUntil)) {
            return Status.ACTIVE;
        }
        if (now.isBefore(trialEndsAt)) {
            return Status.TRIAL;
        }
        return Status.EXPIRED;
    }

    /** Até quando o profissional pode prescrever: a maior entre o fim do teste e o período pago (DEV-0101). */
    public Instant validUntil() {
        return paidUntil != null && paidUntil.isAfter(trialEndsAt) ? paidUntil : trialEndsAt;
    }

    /** Estende o período pago até {@code until}; um pagamento mais antigo que chega depois nunca encurta. */
    public Subscription activateUntil(Instant until) {
        Objects.requireNonNull(until, "until");
        Instant next = paidUntil != null && paidUntil.isAfter(until) ? paidUntil : until;
        return new Subscription(professionalId, plan, trialEndsAt, next, createdAt);
    }
}
