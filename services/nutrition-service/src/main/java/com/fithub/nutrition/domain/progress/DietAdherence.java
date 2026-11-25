package com.fithub.nutrition.domain.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aderência à dieta (DEV-0092, FR-57): refeições feitas contra prescritas no período. Cada refeição prescrita num dia
 * conta 1; a feita vale 1, a parcial 0,5, a não feita ou sem registro 0. As prescritas de um dia são as refeições da
 * versão do plano em vigor nele, enquanto o plano estava publicado.
 */
public final class DietAdherence {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DietAdherence() {}

    /**
     * Uma versão publicada do plano.
     *
     * @param publishedOn o dia em que a versão passou a valer
     * @param meals as refeições dela
     */
    public record Version(LocalDate publishedOn, int meals) {}

    /**
     * Um plano publicado.
     *
     * @param to o último dia em que ele valeu (o da substituição); vazio enquanto continua valendo
     * @param versions as versões publicadas, da primeira para a última
     */
    public record PlanWindow(UUID planId, LocalDate to, List<Version> versions) {}

    /** Um registro: o dia, o plano e como foi (DONE, PARTIAL ou SKIPPED). */
    public record Log(LocalDate day, UUID planId, String status) {}

    /** Um dia do período: o feito (uma casa) e as refeições prescritas. */
    public record Day(LocalDate day, BigDecimal done, int prescribed) {}

    /**
     * O resultado do período.
     *
     * @param percent feitas sobre prescritas, com uma casa; vazio sem refeição prescrita
     */
    public record Result(BigDecimal done, int prescribed, BigDecimal percent, List<Day> days) {}

    public static Result of(ProgressPeriod period, List<PlanWindow> plans, List<Log> logs) {
        List<Day> days = new ArrayList<>();
        BigDecimal totalDone = BigDecimal.ZERO;
        int totalPrescribed = 0;
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            int prescribed = 0;
            BigDecimal done = BigDecimal.ZERO;
            for (PlanWindow plan : plans) {
                int meals = mealsOn(plan, day);
                if (meals > 0) {
                    prescribed += meals;
                    done = done.add(doneOn(logs, plan.planId(), day));
                }
            }
            days.add(new Day(day, done.setScale(1, RoundingMode.HALF_UP), prescribed));
            totalDone = totalDone.add(done);
            totalPrescribed += prescribed;
        }
        BigDecimal percent = totalPrescribed == 0
                ? null
                : totalDone.multiply(HUNDRED).divide(BigDecimal.valueOf(totalPrescribed), 1, RoundingMode.HALF_UP);
        return new Result(totalDone.setScale(1, RoundingMode.HALF_UP), totalPrescribed, percent, days);
    }

    /** As refeições da versão em vigor no dia: a última publicada até ele, se o plano ainda valia. */
    private static int mealsOn(PlanWindow plan, LocalDate day) {
        if (plan.to() != null && day.isAfter(plan.to())) {
            return 0;
        }
        int meals = 0;
        for (Version version : plan.versions()) {
            if (!version.publishedOn().isAfter(day)) {
                meals = version.meals();
            }
        }
        return meals;
    }

    private static BigDecimal doneOn(List<Log> logs, UUID planId, LocalDate day) {
        BigDecimal done = BigDecimal.ZERO;
        for (Log log : logs) {
            if (log.planId().equals(planId) && log.day().equals(day)) {
                done = done.add(valueOf(log.status()));
            }
        }
        return done;
    }

    /** A feita vale 1, a parcial 0,5 e a não feita 0. */
    static BigDecimal valueOf(String status) {
        return switch (status) {
            case "DONE" -> BigDecimal.ONE;
            case "PARTIAL" -> HALF;
            default -> BigDecimal.ZERO;
        };
    }
}
