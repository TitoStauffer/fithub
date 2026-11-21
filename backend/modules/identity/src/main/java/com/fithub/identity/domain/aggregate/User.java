package com.fithub.identity.domain.aggregate;

import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.valueobject.UserRole;
import com.fithub.identity.domain.valueobject.UserStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agregado raiz User: responsável por autenticação e autorização.
 * Invariantes:
 * - Email deve ser único e válido
 * - Password deve ser criptografado (nunca em plaintext)
 * - Status é controlado (ACTIVE, INACTIVE)
 */
public class User {
    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UUID id;
    private final Email email;
    private final String passwordHash;
    private final UserRole role;
    private final String name;
    private UserStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Construtor para criar novo user (factory method preferred)
    private User(UUID id, Email email, String passwordHash, UserRole role, String name,
                 UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method para registrar novo usuário.
     * Valida password e criptografa automaticamente.
     * Email deve estar já validado (via Email.createAndValidate).
     */
    public static User registerNew(Email email, String plainPassword, UserRole role, String name) {
        validatePassword(plainPassword);
        validateName(name);

        String passwordHash = passwordEncoder.encode(plainPassword);
        LocalDateTime now = LocalDateTime.now();

        return new User(
            UUID.randomUUID(),
            email,
            passwordHash,
            role,
            name,
            UserStatus.ACTIVE,
            now,
            now
        );
    }

    /**
     * Factory method para reconstruir user a partir do banco.
     * (Usado por ORM/Repository, sem re-criptografar)
     */
    public static User fromDatabase(UUID id, Email email, String passwordHash, UserRole role,
                                    String name, UserStatus status, LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {
        return new User(id, email, passwordHash, role, name, status, createdAt, updatedAt);
    }

    // ========== Métodos de Domínio ==========

    /**
     * Valida se a senha fornecida corresponde ao hash armazenado.
     */
    public boolean authenticate(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(plainPassword, this.passwordHash);
    }

    /**
     * Desativa o usuário.
     */
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Ativa o usuário.
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    // ========== Getters ==========

    public UUID getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Getter para infraestrutura (JPA)
    public String getPasswordHash() {
        return passwordHash;
    }

    // ========== Validações de Negócio ==========

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password must have at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }
        // Poderia adicionar mais regras (maiúscula, número, etc)
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Name cannot exceed 255 characters");
        }
    }

    @Override
    public String toString() {
        return "User{" +
            "id=" + id +
            ", email=" + email +
            ", role=" + role +
            ", name='" + name + '\'' +
            ", status=" + status +
            ", createdAt=" + createdAt +
            '}';
    }
}
