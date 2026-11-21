package com.fithub.identity.domain.repository;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.valueobject.Email;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface para o agregado User.
 * Abstração entre domínio e infraestrutura.
 */
public interface UserRepository {
    void save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
