package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.aggregate.Exercise;
import com.fithub.training.domain.repository.ExerciseRepository;
import com.fithub.training.domain.valueobject.MuscleGroup;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter implementando ExerciseRepository usando Spring Data JPA
 * Converte entre Domain Aggregates e JPA Entities
 */
@Repository
public class ExerciseRepositoryAdapter implements ExerciseRepository {
    private final ExerciseJpaRepository jpaRepository;

    public ExerciseRepositoryAdapter(ExerciseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Exercise exercise) {
        ExerciseEntity entity = ExerciseEntity.fromAggregate(exercise);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Exercise> findById(UUID id) {
        return jpaRepository.findById(id)
            .map(ExerciseEntity::toAggregate);
    }

    @Override
    public Optional<Exercise> findByExternalId(String externalId) {
        return jpaRepository.findByExternalId(externalId)
            .map(ExerciseEntity::toAggregate);
    }

    @Override
    public List<Exercise> findAllByMuscleGroup(MuscleGroup muscleGroup) {
        return jpaRepository.findByMuscleGroup(muscleGroup.name()).stream()
            .map(ExerciseEntity::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<Exercise> findAll() {
        return jpaRepository.findAll().stream()
            .map(ExerciseEntity::toAggregate)
            .collect(Collectors.toList());
    }
}
