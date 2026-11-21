package com.fithub.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fithub.identity.domain.valueobject.Email;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository para UserEntity
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
}
