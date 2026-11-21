package com.fithub.identity.infrastructure.persistence;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.valueobject.UserRole;
import com.fithub.identity.domain.valueobject.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity para persistência de User.
 * Mapeia o agregado User para o banco de dados.
 */
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Construtores

    public UserEntity() {}

    private UserEntity(UUID id, String email, String passwordHash, UserRole role,
                       String name, UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
     * Factory: converte agregado User para JPA Entity.
     */
    public static UserEntity fromAggregate(User user) {
        return new UserEntity(
            user.getId(),
            user.getEmail().getValue(),
            user.getPasswordHash(),
            user.getRole(),
            user.getName(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    /**
     * Reconstrói agregado User a partir da Entity.
     * Usa Email.fromStorage() para evitar re-validação (já foi validado na persistência).
     */
    public User toAggregate() {
        return User.fromDatabase(
            this.id,
            Email.fromStorage(this.email),
            this.passwordHash,
            this.role,
            this.name,
            this.status,
            this.createdAt,
            this.updatedAt
        );
    }

    // Getters e Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
