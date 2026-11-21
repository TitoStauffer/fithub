package com.fithub.patients.domain.valueobject;

import java.util.Objects;

/**
 * Value Object: dados biométricos do paciente.
 * IMC é calculado automaticamente: weight / (height/100)^2
 */
public class Biometrics {
    private final Double weight;  // kg
    private final Integer age;    // anos
    private final Double height;  // cm
    private final Double imc;     // calculado

    private Biometrics(Double weight, Integer age, Double height) {
        this.weight = weight;
        this.age = age;
        this.height = height;
        this.imc = calculateImc(weight, height);
    }

    public static Biometrics create(Double weight, Integer age, Double height) {
        validateBiometrics(weight, age, height);
        return new Biometrics(weight, age, height);
    }

    private static void validateBiometrics(Double weight, Integer age, Double height) {
        if (weight == null || weight < 30 || weight > 300) {
            throw new IllegalArgumentException("Weight must be between 30 and 300 kg");
        }
        if (age == null || age < 13 || age > 120) {
            throw new IllegalArgumentException("Age must be between 13 and 120 years");
        }
        if (height == null || height < 50 || height > 300) {
            throw new IllegalArgumentException("Height must be between 50 and 300 cm");
        }
    }

    private static Double calculateImc(Double weight, Double height) {
        double heightInMeters = height / 100.0;
        return Math.round((weight / (heightInMeters * heightInMeters)) * 100.0) / 100.0;
    }

    public Double getWeight() { return weight; }
    public Integer getAge() { return age; }
    public Double getHeight() { return height; }
    public Double getImc() { return imc; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Biometrics that = (Biometrics) o;
        return Objects.equals(weight, that.weight) &&
               Objects.equals(age, that.age) &&
               Objects.equals(height, that.height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weight, age, height);
    }

    @Override
    public String toString() {
        return "Biometrics{" +
            "weight=" + weight +
            ", age=" + age +
            ", height=" + height +
            ", imc=" + imc +
            '}';
    }
}
