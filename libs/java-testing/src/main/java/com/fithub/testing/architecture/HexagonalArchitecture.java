package com.fithub.testing.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Regras da arquitetura de cada serviço (ENGENHARIA.md §5). O serviço as aplica no próprio pacote:
 *
 * <pre>{@code
 * @AnalyzeClasses(packagesOf = AccountServiceApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);
 * }
 * }</pre>
 *
 * <p>Os pacotes das camadas são ancorados em {@code com.fithub.<servico>}, para que um pacote de
 * terceiros com o mesmo nome (por exemplo um {@code ...application...} do Spring) nunca conte como
 * camada. Camada ainda vazia não falha a regra: um serviço recém-criado só tem {@code interfaces}.
 */
public final class HexagonalArchitecture {

    static final String DOMAIN = "com.fithub.*.domain..";
    static final String APPLICATION = "com.fithub.*.application..";
    static final String INFRASTRUCTURE = "com.fithub.*.infrastructure..";
    static final String INTERFACES = "com.fithub.*.interfaces..";

    /** Domínio puro: nada de Spring, JPA/Hibernate nem da java-commons (que é Spring). */
    @ArchTest
    public static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "jakarta.transaction..",
                    "com.fithub.commons..")
            .allowEmptyShould(true)
            .because("o domínio não depende de Spring nem de JPA (ENGENHARIA.md §5)");

    /** Caso de uso orquestra portas; persistência, mensageria e HTTP ficam nos adaptadores. */
    @ArchTest
    public static final ArchRule applicationIsAdapterFree = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..", "org.springframework.data..",
                    "org.springframework.amqp..", "org.springframework.web..", "jakarta.servlet..")
            .allowEmptyShould(true)
            .because("JPA, RabbitMQ e HTTP ficam em infrastructure e interfaces");

    /**
     * Direção das dependências: tudo aponta para o domínio. Adaptadores (infrastructure e interfaces)
     * não se conhecem nem são conhecidos pelo núcleo.
     */
    @ArchTest
    public static final ArchRule layersPointInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .layer("Interfaces").definedBy(INTERFACES)
            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces");

    /**
     * Todo código mora numa das quatro camadas, menos a classe {@code @SpringBootApplication} na raiz.
     * Sem esta regra, um pacote com nome errado escaparia de todas as outras em silêncio.
     */
    @ArchTest
    public static final ArchRule everyClassBelongsToALayer = classes()
            .should().resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE, INTERFACES)
            .orShould().beAnnotatedWith(SpringBootApplication.class)
            .because("o serviço segue domain / application / infrastructure / interfaces (ENGENHARIA.md §5)");

    private HexagonalArchitecture() {
    }
}
