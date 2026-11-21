package com.fithub.identity.domain.service;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.event.UserRegistered;
import com.fithub.identity.domain.repository.UserRepository;
import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.valueobject.UserRole;
import org.springframework.stereotype.Service;

/**
 * Domain Service: lógica de negócio para registrar usuários.
 * Encapsula invariantes e regras do agregado User.
 */
@Service
public class UserRegistrationService {
    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    public UserRegistrationService(UserRepository userRepository, DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registra um novo usuário, validando regras de negócio.
     *
     * @throws IllegalArgumentException se email já existe ou dados são inválidos
     */
    public User registerUser(String email, String plainPassword, UserRole role, String name) {
        Email emailVO = Email.createAndValidate(email); // Valida formato

        // Verifica se email já existe
        if (userRepository.existsByEmail(emailVO)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        // Cria novo user (factory method valida password e name)
        User user = User.registerNew(emailVO, plainPassword, role, name);

        // Persiste
        userRepository.save(user);

        // Emite evento de domínio
        UserRegistered event = new UserRegistered(
            user.getId(),
            user.getEmail().getValue(),
            user.getRole(),
            user.getName(),
            user.getCreatedAt()
        );
        eventPublisher.publish(event);

        return user;
    }
}
