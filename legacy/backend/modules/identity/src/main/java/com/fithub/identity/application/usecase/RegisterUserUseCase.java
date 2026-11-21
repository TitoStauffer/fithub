package com.fithub.identity.application.usecase;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.service.UserRegistrationService;
import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.valueobject.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-ID-001: Register User
 * Input: email, password, role, name
 * Output: userId, email, role
 */
@Service
public class RegisterUserUseCase {
    private static final Logger logger = LoggerFactory.getLogger(RegisterUserUseCase.class);
    private final UserRegistrationService userRegistrationService;

    public RegisterUserUseCase(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @Transactional
    public RegisterUserResponse execute(RegisterUserRequest request) {
        logger.debug("Registration attempt for email: {}", request.getEmail());

        try {
            // Registra user (valida email, password, email único, etc)
            User user = userRegistrationService.registerUser(
                request.getEmail(),
                request.getPassword(),
                UserRole.valueOf(request.getRole().toUpperCase()),
                request.getName()
            );

            logger.info("User registered successfully: {} (role: {})", user.getId(), user.getRole());

            return new RegisterUserResponse(
                user.getId().toString(),
                user.getEmail().getValue(),
                user.getRole().name(),
                user.getName()
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during registration", e);
            throw new RuntimeException("Registration failed", e);
        }
    }

    // ========== DTOs ==========

    public static class RegisterUserRequest {
        private String email;
        private String password;
        private String role;
        private String name;

        public RegisterUserRequest(String email, String password, String role, String name) {
            this.email = email;
            this.password = password;
            this.role = role;
            this.name = name;
        }

        public RegisterUserRequest() {}

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class RegisterUserResponse {
        private String userId;
        private String email;
        private String role;
        private String name;

        public RegisterUserResponse(String userId, String email, String role, String name) {
            this.userId = userId;
            this.email = email;
            this.role = role;
            this.name = name;
        }

        public String getUserId() { return userId; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public String getName() { return name; }
    }
}
