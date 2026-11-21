package com.fithub.training.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository para ExerciseEntity
 * Acesso direto ao banco de dados
 */
@Repository
public interface ExerciseJpaRepository extends JpaRepository<ExerciseEntity, UUID> {
    Optional<ExerciseEntity> findByExternalId(String externalId);

    /**
     * Busca exercícios por muscle group (stored as comma-separated)
     */
    @Query("SELECT e FROM ExerciseEntity e WHERE e.muscleGroupsStr LIKE %:muscleGroup%")
    List<ExerciseEntity> findByMuscleGroup(String muscleGroup);
}
