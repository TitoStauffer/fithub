package com.fithub.identity.domain.event;

import com.fithub.identity.domain.valueobject.UserRole;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: usuário foi autenticado com sucesso.
 */
public class UserAuthenticated {
    private final UUID userId;
    private final UserRole role;
    private final LocalDateTime authenticatedAt;

    public UserAuthenticated(UUID userId, UserRole role, LocalDateTime authenticatedAt) {
        this.userId = userId;
        this.role = role;
        this.authenticatedAt = authenticatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }

    public LocalDateTime getAuthenticatedAt() {
        return authenticatedAt;
    }
}
