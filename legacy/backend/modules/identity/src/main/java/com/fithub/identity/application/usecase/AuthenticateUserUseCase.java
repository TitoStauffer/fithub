package com.fithub.identity.application.usecase;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.repository.UserRepository;
import com.fithub.identity.domain.service.DomainEventPublisher;
import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.event.UserAuthenticated;
import com.fithub.identity.infrastructure.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UC-ID-002: Authenticate User
 * Input: email, password
 * Output: accessToken, refreshToken, role
 *
 * Retorna SEMPRE mensagem genérica para evitar user enumeration attacks.
 */
@Service
public class AuthenticateUserUseCase {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticateUserUseCase.class);
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final DomainEventPublisher eventPublisher;

    public AuthenticateUserUseCase(UserRepository userRepository,
                                   JwtTokenProvider jwtTokenProvider,
                                   DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AuthenticateUserResponse execute(AuthenticateUserRequest request) {
        logger.debug("Authentication attempt for email: {}", request.getEmail());

        try {
            // Valida email com regex RFC
            Email email = Email.createAndValidate(request.getEmail());

            // Busca usuário pelo email
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password"));

            // Valida password
            if (!user.authenticate(request.getPassword())) {
                logger.warn("Failed authentication: invalid password for user: {}", user.getId());
                // IMPORTANTE: Retorna MESMA mensagem que "user not found" para evitar enumeration
                throw new AuthenticationFailedException("Invalid email or password");
            }

            // Valida status (ativo)
            if (!user.isActive()) {
                logger.warn("Failed authentication: inactive user: {}", user.getId());
                throw new AuthenticationFailedException("Invalid email or password");
            }

            // Gera tokens
            String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            // Emite evento
            eventPublisher.publish(new UserAuthenticated(user.getId(), user.getRole(), LocalDateTime.now()));

            logger.info("User authenticated successfully: {} (role: {})", user.getId(), user.getRole());

            return new AuthenticateUserResponse(accessToken, refreshToken, user.getRole().name());

        } catch (AuthenticationFailedException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during authentication", e);
            throw new RuntimeException("Authentication failed", e);
        }
    }

    // ========== Custom Exception ==========

    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException(String message) {
            super(message);
        }
    }

    // ========== DTOs ==========

    public static class AuthenticateUserRequest {
        private String email;
        private String password;

        public AuthenticateUserRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public AuthenticateUserRequest() {}

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AuthenticateUserResponse {
        private String accessToken;
        private String refreshToken;
        private String role;

        public AuthenticateUserResponse(String accessToken, String refreshToken, String role) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.role = role;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getRole() { return role; }
    }
}
