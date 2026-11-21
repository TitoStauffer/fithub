package com.fithub.identity.infrastructure.persistence;

import com.fithub.identity.domain.aggregate.User;
import com.fithub.identity.domain.repository.UserRepository;
import com.fithub.identity.domain.valueobject.Email;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JPA do UserRepository (Domain Interface)
 * Adapta UserEntity para User aggregate usando factory methods
 */
@Repository
public class JpaUserRepository implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    public JpaUserRepository(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public void save(User user) {
        UserEntity entity = UserEntity.fromAggregate(user);
        userJpaRepository.save(entity);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return userJpaRepository.findByEmail(email.getValue())
            .map(UserEntity::toAggregate);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id)
            .map(UserEntity::toAggregate);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return userJpaRepository.findByEmail(email.getValue()).isPresent();
    }
}
