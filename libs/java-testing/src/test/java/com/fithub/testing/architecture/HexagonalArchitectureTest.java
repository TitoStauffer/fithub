package com.fithub.testing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Cada regra pega a violação que existe para pegar e deixa passar um serviço correto. Os fixtures são
 * serviços de mentira em {@code com.fithub.<cenario>}, um por violação.
 */
class HexagonalArchitectureTest {

    private static final ArchRule[] ALL_RULES = {
            HexagonalArchitecture.domainIsFrameworkFree,
            HexagonalArchitecture.applicationIsAdapterFree,
            HexagonalArchitecture.layersPointInward,
            HexagonalArchitecture.everyClassBelongsToALayer,
    };

    @Test
    void aServiceThatFollowsTheLayersPassesEveryRule() {
        JavaClasses service = classesOf("okservice");
        for (ArchRule rule : ALL_RULES) {
            assertThat(rule.evaluate(service).hasViolation()).as(rule.getDescription()).isFalse();
        }
    }

    @Test
    void aServiceWithOnlySomeLayersStillPasses() {
        // Esqueleto recém-criado: camadas vazias não podem derrubar as regras.
        JavaClasses onlyOneLayer = classesOf("interfacestoinfra.infrastructure");
        for (ArchRule rule : ALL_RULES) {
            assertThat(rule.evaluate(onlyOneLayer).hasViolation()).as(rule.getDescription()).isFalse();
        }
    }

    @Test
    void domainAnnotatedWithSpringIsRejected() {
        assertViolation(HexagonalArchitecture.domainIsFrameworkFree, "springindomain");
    }

    @Test
    void domainMappedWithJpaIsRejected() {
        assertViolation(HexagonalArchitecture.domainIsFrameworkFree, "jpaindomain");
    }

    @Test
    void useCaseTalkingToJpaIsRejected() {
        assertViolation(HexagonalArchitecture.applicationIsAdapterFree, "jpainapplication");
    }

    @Test
    void controllerReachingTheInfrastructureIsRejected() {
        assertViolation(HexagonalArchitecture.layersPointInward, "interfacestoinfra");
    }

    @Test
    void domainDependingOnTheApplicationIsRejected() {
        assertViolation(HexagonalArchitecture.layersPointInward, "domaintoapplication");
    }

    @Test
    void classOutsideTheLayersIsRejected() {
        assertViolation(HexagonalArchitecture.everyClassBelongsToALayer, "stray");
    }

    private static void assertViolation(ArchRule rule, String scenario) {
        assertThat(rule.evaluate(classesOf(scenario)).hasViolation()).as(rule.getDescription()).isTrue();
    }

    private static JavaClasses classesOf(String scenario) {
        return new ClassFileImporter().importPackages("com.fithub." + scenario);
    }
}
