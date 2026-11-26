package com.fithub.account.domain.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Preço do plano mensal (DEV-0098, PRD seção 9.2): o cheio e o de lançamento, que vale nos {@code launchMonths}
 * primeiros meses pagos de quem assina até {@code launchUntil} (inclusive, no dia de São Paulo).
 */
public record Pricing(BigDecimal monthly, BigDecimal launch, int launchMonths, LocalDate launchUntil) {

    public Pricing {
        Objects.requireNonNull(monthly, "monthly");
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(launchUntil, "launchUntil");
        // O preço cheio maior que zero vem das duas regras juntas: lançamento acima de zero e não acima do cheio.
        if (launch.signum() <= 0) {
            throw new IllegalArgumentException("Os preços precisam ser maiores que zero.");
        }
        if (launch.compareTo(monthly) > 0) {
            throw new IllegalArgumentException("O preço de lançamento não pode passar do preço cheio.");
        }
        if (launchMonths < 1) {
            throw new IllegalArgumentException("O preço de lançamento precisa valer ao menos 1 mês.");
        }
    }

    /** A oferta para quem assina em {@code today}: o preço de lançamento até a data de corte, o cheio depois. */
    public Offer offerOn(LocalDate today) {
        return today.isAfter(launchUntil) ? new Offer(monthly, false) : new Offer(launch, true);
    }

    /** O valor da primeira cobrança e se ele é o de lançamento. */
    public record Offer(BigDecimal value, boolean launch) {}
}
