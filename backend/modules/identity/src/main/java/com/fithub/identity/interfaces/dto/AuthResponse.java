package com.fithub.identity.interfaces.dto;

/**
 * DTO para resposta de autenticação.
 */
public class AuthResponse {
    private String userId;
    private String email;
    private String role;
    private String name;
    private String accessToken;
    private String refreshToken;

    // Construtores
    public AuthResponse() {}

    public AuthResponse(String userId, String email, String role, String name,
                       String accessToken, String refreshToken) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.name = name;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    // Getters e Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
