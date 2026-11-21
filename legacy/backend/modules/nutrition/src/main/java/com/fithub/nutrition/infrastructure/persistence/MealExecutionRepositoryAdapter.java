package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.aggregate.MealExecution;
import com.fithub.nutrition.domain.repository.MealExecutionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementando MealExecutionRepository usando Spring Data JPA
 */
@Repository
public class MealExecutionRepositoryAdapter implements MealExecutionRepository {
    private final NutritionMealExecutionJpaRepository jpaRepository;

    public MealExecutionRepositoryAdapter(NutritionMealExecutionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(MealExecution execution) {
        // Não implementado - usar Spring Data diretamente para entidade
        throw new UnsupportedOperationException("Use NutritionMealExecutionJpaRepository.save(entity) directly");
    }

    @Override
    public Optional<MealExecution> findById(UUID id) {
        // Não implementado - usar Spring Data diretamente
        throw new UnsupportedOperationException("Use NutritionMealExecutionJpaRepository.findById(id) directly");
    }

    @Override
    public List<MealExecution> findByPatientIdAndDate(UUID patientId, LocalDate date) {
        return List.of(); // TODO: Implementar conversão de entidade para aggregate
    }

    @Override
    public List<MealExecution> findByPatientIdLast30Days(UUID patientId) {
        return List.of(); // TODO: Implementar conversão de entidade para aggregate
    }
}
