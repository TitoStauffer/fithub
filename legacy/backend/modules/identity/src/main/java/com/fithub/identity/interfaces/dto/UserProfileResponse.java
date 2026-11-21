package com.fithub.identity.interfaces.dto;

/**
 * Response DTO para GET /auth/me
 * Retorna dados completos do usuário autenticado
 */
public class UserProfileResponse {
    private String userId;
    private String email;
    private String name;
    private String role;

    public UserProfileResponse(String userId, String email, String name, String role) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }
}
