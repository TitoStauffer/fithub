package com.fithub.training.domain.progress;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Período das consultas de evolução (DEV-0091), em dias de America/Sao_Paulo, com as duas pontas incluídas. Sem
 * informar, as últimas 4 semanas até hoje; no máximo 26 semanas.
 */
public record ProgressPeriod(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 28;
    public static final int MAX_DAYS = 26 * 7;

    /** O período pedido, com os padrões para o que veio vazio. */
    public static ProgressPeriod of(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end)) {
            throw new InvalidProgressException("from", "O início do período precisa ser antes do fim.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new InvalidProgressException("from", "O período vai até 26 semanas.");
        }
        return new ProgressPeriod(start, end);
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }
}
