package com.fithub.client.domain.assessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Série histórica da evolução corporal (DEV-0093, FR-56 a FR-58): o peso, cada circunferência e o % de gordura de cada
 * método, das avaliações em ordem de data. Métodos diferentes nunca se misturam numa série (BR-12): cada um tem a sua.
 *
 * @param circumferences uma série por circunferência medida ao menos uma vez, na ordem do formulário
 * @param bodyFat uma série por método de % de gordura que tem ao menos um valor
 */
public record BodySeries(
        List<Point> weightKg, Map<String, List<Point>> circumferences, Map<FatMethod, List<Point>> bodyFat) {

    /** Um valor numa data de medição. */
    public record Point(LocalDate date, BigDecimal value) {}

    /** A origem do % de gordura: cada fórmula e a bioimpedância informada são séries separadas. */
    public enum FatMethod {
        POLLOCK_3,
        POLLOCK_7,
        NAVY,
        BIOIMPEDANCE
    }

    private static final Map<String, Function<Circumferences, BigDecimal>> CIRCUMFERENCES = circumferenceFields();

    private static final Map<FatMethod, Function<BodyFat, BigDecimal>> FAT = Map.of(
            FatMethod.POLLOCK_3, BodyFat::pollock3,
            FatMethod.POLLOCK_7, BodyFat::pollock7,
            FatMethod.NAVY, BodyFat::navy,
            FatMethod.BIOIMPEDANCE, BodyFat::bioimpedance);

    /**
     * A série das avaliações no período; sem as datas, todo o histórico.
     *
     * @throws InvalidAssessmentException se o início vem depois do fim
     */
    public static BodySeries of(List<Assessment> assessments, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidAssessmentException("from", "O início do período precisa ser antes do fim.");
        }
        List<Assessment> ordered = assessments.stream()
                .filter(assessment -> from == null || !assessment.measuredOn().isBefore(from))
                .filter(assessment -> to == null || !assessment.measuredOn().isAfter(to))
                .sorted(Comparator.comparing(Assessment::measuredOn).thenComparing(Assessment::createdAt))
                .toList();
        List<Point> weight = points(ordered, Assessment::weightKg);
        Map<String, List<Point>> circumferences = new LinkedHashMap<>();
        CIRCUMFERENCES.forEach((name, field) -> {
            List<Point> series = points(ordered, assessment -> field.apply(assessment.circumferences()));
            if (!series.isEmpty()) {
                circumferences.put(name, series);
            }
        });
        Map<FatMethod, List<Point>> bodyFat = new LinkedHashMap<>();
        for (FatMethod method : FatMethod.values()) {
            List<Point> series = points(ordered, assessment -> FAT.get(method).apply(assessment.bodyFat()));
            if (!series.isEmpty()) {
                bodyFat.put(method, series);
            }
        }
        return new BodySeries(weight, circumferences, bodyFat);
    }

    /** Os pontos de um valor, pulando as avaliações em que ele não foi medido. */
    private static List<Point> points(List<Assessment> ordered, Function<Assessment, BigDecimal> value) {
        List<Point> points = new ArrayList<>();
        for (Assessment assessment : ordered) {
            BigDecimal measured = value.apply(assessment);
            if (measured != null) {
                points.add(new Point(assessment.measuredOn(), measured));
            }
        }
        return points;
    }

    private static Map<String, Function<Circumferences, BigDecimal>> circumferenceFields() {
        Map<String, Function<Circumferences, BigDecimal>> fields = new LinkedHashMap<>();
        fields.put("neck", Circumferences::neck);
        fields.put("chest", Circumferences::chest);
        fields.put("waist", Circumferences::waist);
        fields.put("abdomen", Circumferences::abdomen);
        fields.put("hip", Circumferences::hip);
        fields.put("arm", Circumferences::arm);
        fields.put("forearm", Circumferences::forearm);
        fields.put("thigh", Circumferences::thigh);
        fields.put("calf", Circumferences::calf);
        return fields;
    }
}
