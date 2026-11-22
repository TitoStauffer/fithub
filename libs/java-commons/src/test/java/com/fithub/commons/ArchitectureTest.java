package com.fithub.commons;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** A java-commons é só técnica (AGENTS.md): nada de domínio dos serviços nem de persistência de domínio. */
@AnalyzeClasses(packages = "com.fithub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule onlyCommonsPackages = classes()
            .should().resideInAPackage("com.fithub.commons..")
            .because("a biblioteca não pode carregar código de serviço");

    @ArchTest
    static final ArchRule noDomainPackages = noClasses()
            .should().resideInAPackage("..domain..")
            .because("domínio compartilhado entre serviços é proibido");

    @ArchTest
    static final ArchRule noJpaEntities = noClasses()
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .because("entidades pertencem aos serviços, não à biblioteca técnica");
}
