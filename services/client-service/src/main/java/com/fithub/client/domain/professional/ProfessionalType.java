package com.fithub.client.domain.professional;

import java.util.Arrays;

/** Tipo do profissional, que também é o tipo do vínculo (1 personal e 1 nutricionista por cliente, BR-02). */
public enum ProfessionalType {
    PERSONAL_TRAINER("personal_trainer"),
    NUTRITIONIST("nutritionist");

    private final String contractValue;

    ProfessionalType(String contractValue) {
        this.contractValue = contractValue;
    }

    /** Valor usado nos contratos de evento ({@code personal_trainer} ou {@code nutritionist}). */
    public String contractValue() {
        return contractValue;
    }

    public static ProfessionalType fromContract(String value) {
        return Arrays.stream(values())
                .filter(type -> type.contractValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de profissional fora do contrato."));
    }
}
