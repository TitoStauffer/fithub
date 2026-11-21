package com.fithub.patients.domain.aggregate;

import com.fithub.patients.domain.valueobject.Biometrics;
import com.fithub.patients.domain.valueobject.RestrictionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD: Paciente e validações biométricas
 */
class PatientTest {

    @Test
    void shouldCreateNewPatient() {
        UUID userId = UUID.randomUUID();

        Patient patient = Patient.create(userId, "João Silva", 70.0, 25, 175.0);

        assertNotNull(patient.getId());
        assertEquals("João Silva", patient.getName());
        assertEquals(70.0, patient.getBiometrics().getWeight());
        assertEquals("ACTIVE", patient.getStatus().toString());
    }

    @Test
    void shouldRejectZeroWeight() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
            () -> Patient.create(userId, "Test", 0.0, 25, 175.0));
    }

    @Test
    void shouldCalculateIMCCorrectly() {
        UUID userId = UUID.randomUUID();
        // Height in cm: 180cm = 1.80m
        // IMC = 80 / (1.80^2) = 24.69
        Patient patient = Patient.create(userId, "Maria", 80.0, 30, 180.0);

        double imc = patient.getBiometrics().getImc();
        assertEquals(24.69, imc);
    }

    @Test
    void shouldAddHealthRestriction() {
        UUID userId = UUID.randomUUID();
        Patient patient = Patient.create(userId, "Maria", 65.0, 28, 170.0);

        HealthRestriction restriction = HealthRestriction.create(RestrictionType.DIABETES, "Type 2");
        patient.addRestriction(restriction);

        assertEquals(1, patient.getRestrictions().size());
    }

    @Test
    void shouldDeactivatePatient() {
        UUID userId = UUID.randomUUID();
        Patient patient = Patient.create(userId, "Pedro", 75.0, 35, 178.0);

        patient.deactivate();
        assertEquals("INACTIVE", patient.getStatus().toString());
    }

    @Test
    void shouldUpdateBiometrics() {
        UUID userId = UUID.randomUUID();
        Patient patient = Patient.create(userId, "João", 75.0, 30, 180.0);

        patient.updateBiometrics(80.0, 30, 180.0);

        assertEquals(80.0, patient.getBiometrics().getWeight());
    }
}
