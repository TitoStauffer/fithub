package com.fithub.client.domain.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Fórmulas de % de gordura (DEV-0045). Densidade corporal pelos protocolos de Jackson & Pollock e % de gordura pela
 * equação de Siri (495/D − 450), com 1 casa decimal. Sem o sexo, a idade ou alguma dobra do protocolo, não há
 * resultado: a avaliação é gravada sem ele.
 */
public final class BodyFatFormulas {

    private BodyFatFormulas() {}

    /** Pollock 3 dobras: homem peitoral, abdominal e coxa; mulher tríceps, suprailíaca e coxa. */
    public static Optional<BigDecimal> pollock3(Assessment.Sex sex, Integer age, Skinfolds folds) {
        if (sex == null || age == null) {
            return Optional.empty();
        }
        return sex == Assessment.Sex.MALE
                ? sum(folds.chest(), folds.abdomen(), folds.thigh())
                        .map(s -> 1.10938 - 0.0008267 * s + 0.0000016 * s * s - 0.0002574 * age)
                        .map(BodyFatFormulas::siri)
                : sum(folds.triceps(), folds.suprailiac(), folds.thigh())
                        .map(s -> 1.0994921 - 0.0009929 * s + 0.0000023 * s * s - 0.0001392 * age)
                        .map(BodyFatFormulas::siri);
    }

    /** Pollock 7 dobras: as sete, com as constantes de cada sexo. */
    public static Optional<BigDecimal> pollock7(Assessment.Sex sex, Integer age, Skinfolds folds) {
        if (sex == null || age == null) {
            return Optional.empty();
        }
        Optional<Double> sum = sum(
                folds.chest(),
                folds.midaxillary(),
                folds.triceps(),
                folds.subscapular(),
                folds.abdomen(),
                folds.suprailiac(),
                folds.thigh());
        return sex == Assessment.Sex.MALE
                ? sum.map(s -> 1.112 - 0.00043499 * s + 0.00000055 * s * s - 0.00028826 * age)
                        .map(BodyFatFormulas::siri)
                : sum.map(s -> 1.097 - 0.00046971 * s + 0.00000056 * s * s - 0.00012828 * age)
                        .map(BodyFatFormulas::siri);
    }

    /** Equação de Siri: densidade corporal para % de gordura, com 1 casa decimal. */
    static BigDecimal siri(double bodyDensity) {
        return BigDecimal.valueOf(495 / bodyDensity - 450).setScale(1, RoundingMode.HALF_UP);
    }

    private static Optional<Double> sum(BigDecimal... values) {
        double total = 0;
        for (BigDecimal value : values) {
            if (value == null) {
                return Optional.empty();
            }
            total += value.doubleValue();
        }
        return Optional.of(total);
    }
}
