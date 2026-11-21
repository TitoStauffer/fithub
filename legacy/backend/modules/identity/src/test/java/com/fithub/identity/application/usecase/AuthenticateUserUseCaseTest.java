package com.fithub.identity.application.usecase;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.repository.UserRepository;
import com.fithub.identity.domain.service.DomainEventPublisher;
import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.valueobject.UserRole;
import com.fithub.identity.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BDD: Autenticação resiste a user enumeration
 */
@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private DomainEventPublisher eventPublisher;

    private AuthenticateUserUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new AuthenticateUserUseCase(userRepository, jwtTokenProvider, eventPublisher);
    }

    @Test
    void shouldAuthenticateValidUser() {
        Email email = Email.createAndValidate("user@test.com");
        User user = User.registerNew(email, "correctPass", UserRole.PATIENT, "Test User");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(user.getId(), user.getRole())).thenReturn("access_token_123");
        when(jwtTokenProvider.generateRefreshToken(user.getId())).thenReturn("refresh_token_456");

        AuthenticateUserUseCase.AuthenticateUserRequest request = new AuthenticateUserUseCase.AuthenticateUserRequest("user@test.com", "correctPass");
        AuthenticateUserUseCase.AuthenticateUserResponse response = useCase.execute(request);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("access_token_123", response.getAccessToken());
    }

    @Test
    void shouldRejectInvalidPassword() {
        Email email = Email.createAndValidate("user@test.com");
        User user = User.registerNew(email, "correctPass", UserRole.PATIENT, "Test User");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthenticateUserUseCase.AuthenticateUserRequest request = new AuthenticateUserUseCase.AuthenticateUserRequest("user@test.com", "wrongPass");

        assertThrows(Exception.class, () -> useCase.execute(request));
    }

    @Test
    void shouldNotEnumerateNonexistentUsers() {
        Email email = Email.createAndValidate("nonexistent@test.com");
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        AuthenticateUserUseCase.AuthenticateUserRequest request = new AuthenticateUserUseCase.AuthenticateUserRequest("nonexistent@test.com", "anyPassword");

        assertThrows(Exception.class, () -> useCase.execute(request));
    }
}
