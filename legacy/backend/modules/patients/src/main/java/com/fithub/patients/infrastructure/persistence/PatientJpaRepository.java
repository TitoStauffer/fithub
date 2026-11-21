package com.fithub.patients.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository para PatientEntity
 */
@Repository
public interface PatientJpaRepository extends JpaRepository<PatientEntity, UUID> {
    Optional<PatientEntity> findByUserId(UUID userId);

    /**
     * Busca todos os pacientes de um profissional via PatientProfessionalRelationship
     */
    @Query("SELECT DISTINCT p FROM PatientEntity p " +
           "INNER JOIN PatientProfessionalRelationshipEntity ppr ON ppr.patientId = p.id " +
           "WHERE ppr.professionalId = :professionalId AND ppr.status = 'ACTIVE' " +
           "ORDER BY p.createdAt DESC")
    List<PatientEntity> findByProfessionalUserId(@Param("professionalId") UUID professionalId);
}
