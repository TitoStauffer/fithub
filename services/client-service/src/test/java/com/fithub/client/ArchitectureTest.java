package com.fithub.client;

import com.fithub.testing.architecture.HexagonalArchitecture;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/** domain / application / infrastructure / interfaces, com o domínio livre de Spring e JPA. */
@AnalyzeClasses(packagesOf = ClientServiceApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);
}
