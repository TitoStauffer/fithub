package com.fithub.identity.interfaces.dto;

/**
 * DTO para resposta de validação de token.
 */
public class ValidateTokenResponse {
    private String userId;
    private String role;

    public ValidateTokenResponse(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public String getRole() { return role; }
}
