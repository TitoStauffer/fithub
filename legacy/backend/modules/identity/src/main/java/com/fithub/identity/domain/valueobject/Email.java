package com.fithub.identity.domain.valueobject;

import org.apache.commons.validator.routines.EmailValidator;

import java.util.Objects;

/**
 * Value Object: Email
 * Invariante: email deve ser válido e único no sistema
 */
public class Email {
    private final String value;
    private static final EmailValidator validator = EmailValidator.getInstance(false);

    private Email(String value) {
        this.value = value;
    }

    /**
     * Factory: cria email com validação.
     * Usa Apache Commons EmailValidator para validação RFC 5322.
     */
    public static Email createAndValidate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String normalized = value.toLowerCase().trim();
        if (!validator.isValid(normalized)) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }

        return new Email(normalized);
    }

    /**
     * Factory: reconstrói email a partir do banco de dados.
     * Assume que email já foi validado na persistência, evita re-validação.
     * Performance: não executa validação regex.
     */
    public static Email fromStorage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email from storage cannot be null");
        }
        return new Email(value.toLowerCase());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
