package com.fithub.client.domain.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Fórmulas de % de gordura (DEV-0045). Densidade corporal pelos protocolos de Jackson & Pollock e % de gordura pela
 * equação de Siri (495/D − 450), com 1 casa decimal. Sem o sexo, a idade ou alguma dobra do protocolo, não há
 * resultado: a avaliação é gravada sem ele.
 *
 * <p>As equações foram validadas em adultos: com idade abaixo de 18 (inclusive negativa, quando a medição é anterior
 * ao nascimento do perfil) não há resultado. Resultado fora de 3 a 70% também é descartado, porque as dobras nos
 * extremos da faixa levam a equação a valores sem sentido (até negativos).
 */
public final class BodyFatFormulas {

    /** Idade mínima das equações de Jackson & Pollock. */
    public static final int MIN_AGE = 18;

    /** Faixa plausível do % de gordura, a mesma aceita na bioimpedância. */
    public static final BigDecimal MIN_PERCENT = new BigDecimal("3");

    public static final BigDecimal MAX_PERCENT = new BigDecimal("70");

    private BodyFatFormulas() {}

    /** Pollock 3 dobras: homem peitoral, abdominal e coxa; mulher tríceps, suprailíaca e coxa. */
    public static Optional<BigDecimal> pollock3(Assessment.Sex sex, Integer age, Skinfolds folds) {
        if (!computable(sex, age)) {
            return Optional.empty();
        }
        return sex == Assessment.Sex.MALE
                ? sum(folds.chest(), folds.abdomen(), folds.thigh())
                        .map(s -> 1.10938 - 0.0008267 * s + 0.0000016 * s * s - 0.0002574 * age)
                        .flatMap(BodyFatFormulas::siri)
                : sum(folds.triceps(), folds.suprailiac(), folds.thigh())
                        .map(s -> 1.0994921 - 0.0009929 * s + 0.0000023 * s * s - 0.0001392 * age)
                        .flatMap(BodyFatFormulas::siri);
    }

    /** Pollock 7 dobras: as sete, com as constantes de cada sexo. */
    public static Optional<BigDecimal> pollock7(Assessment.Sex sex, Integer age, Skinfolds folds) {
        if (!computable(sex, age)) {
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
                        .flatMap(BodyFatFormulas::siri)
                : sum.map(s -> 1.097 - 0.00046971 * s + 0.00000056 * s * s - 0.00012828 * age)
                        .flatMap(BodyFatFormulas::siri);
    }

    /** Há sexo para a fórmula e idade de adulto (as equações não valem abaixo de 18 anos). */
    private static boolean computable(Assessment.Sex sex, Integer age) {
        return sex != null && age != null && age >= MIN_AGE;
    }

    /** Equação de Siri: densidade corporal para % de gordura, com 1 casa decimal; vazio fora da faixa plausível. */
    static Optional<BigDecimal> siri(double bodyDensity) {
        BigDecimal percent = BigDecimal.valueOf(495 / bodyDensity - 450).setScale(1, RoundingMode.HALF_UP);
        return percent.compareTo(MIN_PERCENT) < 0 || percent.compareTo(MAX_PERCENT) > 0
                ? Optional.empty()
                : Optional.of(percent);
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
