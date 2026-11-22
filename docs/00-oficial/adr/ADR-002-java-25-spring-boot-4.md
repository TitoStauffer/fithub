# ADR-002 — Java 25 e Spring Boot 4

**Status:** aceito · **Data:** 21/11/2026 · **Decisor:** Tito

## Contexto

Os serviços nascem do zero. O código da primeira versão (`legacy/`) usava Java 21 e Spring Boot 3.3.

## Decisão

Java 25 (LTS) e Spring Boot 4 em todos os serviços, com Maven e um `pom.xml` parent no monorepo.
Spring MVC com virtual threads (sem WebFlux). Versão fixada no parent `pom.xml` (DEV-0011): Spring Boot **4.1.3** (a 4.2.0
saiu em 19/11/2026 e entra depois do primeiro patch). O enforcer recusa build com Java abaixo do 25.

## Alternativas consideradas

- **Java 21 + Spring Boot 3.x:** reaproveitaria o legado diretamente, mas nasceria precisando de
  upgrade.

## Consequências

- Algumas bibliotecas podem demorar a suportar Java 25 / Boot 4: verificar compatibilidade ao
  adotar cada uma.
- Máquinas de desenvolvimento precisam do JDK 25 (`.java-version`); o CI instala o JDK próprio.
