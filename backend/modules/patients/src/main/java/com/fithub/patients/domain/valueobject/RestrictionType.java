package com.fithub.patients.domain.valueobject;

public enum RestrictionType {
    // Restrições de saúde
    KNEE_SURGERY("Cirurgia de joelho"),
    SHOULDER_INJURY("Lesão de ombro"),
    SPINE_ISSUE("Problema de coluna"),
    HIP_SURGERY("Cirurgia de quadril"),
    ANKLE_INJURY("Lesão de tornozelo"),
    ELBOW_INJURY("Lesão de cotovelo"),
    WRIST_INJURY("Lesão de pulso"),
    BACK_PAIN("Dor nas costas"),
    KNEE_PAIN("Dor no joelho"),
    ROTATOR_CUFF("Lesão do manguito rotador"),

    // Alergias e intolerâncias
    LACTOSE_INTOLERANCE("Intolerância à lactose"),
    GLUTEN_INTOLERANCE("Intolerância ao glúten"),
    NUT_ALLERGY("Alergia a nozes"),
    PEANUT_ALLERGY("Alergia a amendoim"),
    SHELLFISH_ALLERGY("Alergia a frutos do mar"),
    EGG_ALLERGY("Alergia a ovos"),

    // Outras condições
    HYPERTENSION("Hipertensão"),
    DIABETES("Diabetes"),
    ASTHMA("Asma"),
    HEART_CONDITION("Condição cardíaca");

    private final String description;

    RestrictionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
