package com.fithub.identity.domain.valueobject;

public enum UserRole {
    PT("Personal Trainer"),
    NUTRITIONIST("Nutricionista"),
    PATIENT("Paciente");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
