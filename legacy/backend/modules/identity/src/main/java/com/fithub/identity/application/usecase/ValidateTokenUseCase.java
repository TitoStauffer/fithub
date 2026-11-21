package com.fithub.identity.application.usecase;

import com.fithub.identity.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * UC-ID-003: Validate Token
 * Input: token (JWT)
 * Output: userId, role
 * Comportamento: valida signature + expiração, retorna claims
 */
@Service
public class ValidateTokenUseCase {
    private final JwtTokenProvider jwtTokenProvider;

    public ValidateTokenUseCase(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public ValidateTokenResponse execute(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        UUID userId = jwtTokenProvider.extractUserId(token);
        String role = jwtTokenProvider.extractRole(token);

        return new ValidateTokenResponse(userId.toString(), role);
    }

    // ========== DTO ==========

    public static class ValidateTokenResponse {
        private String userId;
        private String role;

        public ValidateTokenResponse(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        public String getUserId() { return userId; }
        public String getRole() { return role; }
    }
}
