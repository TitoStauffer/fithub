package com.fithub.identity.domain.event;

import com.fithub.identity.domain.valueobject.UserRole;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: usuário foi registrado com sucesso.
 */
public class UserRegistered {
    private final UUID userId;
    private final String email;
    private final UserRole role;
    private final String name;
    private final LocalDateTime registeredAt;

    public UserRegistered(UUID userId, String email, UserRole role, String name, LocalDateTime registeredAt) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.name = name;
        this.registeredAt = registeredAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}
