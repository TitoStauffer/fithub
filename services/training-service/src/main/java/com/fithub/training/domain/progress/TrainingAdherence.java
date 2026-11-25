package com.fithub.training.domain.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aderência ao treino (DEV-0091, FR-56): treinos finalizados contra esperados no período. A ficha não tem frequência
 * própria, então cada ficha espera, por semana, tantos treinos quanto as suas divisões (uma ficha ABC espera 3), na
 * proporção dos dias em que esteve publicada. O período é dividido em semanas de 7 dias a partir do início; a última
 * pode ser menor.
 */
public final class TrainingAdherence {

    private static final BigDecimal SEVEN = BigDecimal.valueOf(7);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TrainingAdherence() {}

    /**
     * Uma ficha publicada no período.
     *
     * @param from o dia da primeira publicação
     * @param to o último dia em que ela valeu (o dia em que foi substituída); vazio enquanto continua valendo
     * @param divisions os treinos da ficha (A, B, C...), o esperado por semana
     */
    public record PlanWindow(UUID planId, LocalDate from, LocalDate to, int divisions) {}

    /** Uma semana do período, com os treinos feitos e os esperados (uma casa). */
    public record Week(LocalDate start, LocalDate end, int done, BigDecimal expected) {}

    /**
     * O resultado do período.
     *
     * @param percent feitos sobre esperados, com uma casa; vazio sem treino esperado
     */
    public record Result(int done, BigDecimal expected, BigDecimal percent, List<Week> weeks) {}

    public static Result of(ProgressPeriod period, List<PlanWindow> plans, List<LocalDate> finishedDays) {
        List<Week> weeks = new ArrayList<>();
        BigDecimal totalExpected = BigDecimal.ZERO;
        int totalDone = 0;
        for (LocalDate start = period.from(); !start.isAfter(period.to()); start = start.plusDays(7)) {
            LocalDate end = start.plusDays(6).isAfter(period.to()) ? period.to() : start.plusDays(6);
            BigDecimal expected = expectedBetween(plans, start, end);
            int done = doneBetween(finishedDays, start, end);
            weeks.add(new Week(start, end, done, expected.setScale(1, RoundingMode.HALF_UP)));
            totalExpected = totalExpected.add(expected);
            totalDone += done;
        }
        BigDecimal percent = totalExpected.signum() == 0
                ? null
                : BigDecimal.valueOf(totalDone).multiply(HUNDRED).divide(totalExpected, 1, RoundingMode.HALF_UP);
        return new Result(totalDone, totalExpected.setScale(1, RoundingMode.HALF_UP), percent, weeks);
    }

    /** O esperado entre os dois dias: as divisões de cada ficha vezes os dias em que ela valeu, sobre 7. */
    private static BigDecimal expectedBetween(List<PlanWindow> plans, LocalDate start, LocalDate end) {
        BigDecimal expected = BigDecimal.ZERO;
        for (PlanWindow plan : plans) {
            LocalDate from = plan.from().isAfter(start) ? plan.from() : start;
            LocalDate to = plan.to() == null || plan.to().isAfter(end) ? end : plan.to();
            if (!from.isAfter(to)) {
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                expected = expected.add(
                        BigDecimal.valueOf(plan.divisions() * days).divide(SEVEN, 6, RoundingMode.HALF_UP));
            }
        }
        return expected;
    }

    private static int doneBetween(List<LocalDate> finishedDays, LocalDate start, LocalDate end) {
        int done = 0;
        for (LocalDate day : finishedDays) {
            if (!day.isBefore(start) && !day.isAfter(end)) {
                done++;
            }
        }
        return done;
    }
}
