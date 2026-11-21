package com.fithub.patients.domain.valueobject;

public enum ProfessionalRole {
    PT("Personal Trainer"),
    NUTRITIONIST("Nutricionista");

    private final String description;

    ProfessionalRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
