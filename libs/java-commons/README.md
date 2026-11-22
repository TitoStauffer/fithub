# java-commons

Base técnica comum dos serviços (DEV-0013). Só técnica: nenhum tipo de domínio entra aqui, e o
`ArchitectureTest` falha se entrar.

Basta declarar a dependência no serviço; tudo se ativa por autoconfiguração do Spring Boot:

```xml
<dependency>
  <groupId>com.fithub</groupId>
  <artifactId>java-commons</artifactId>
  <version>${project.version}</version>
</dependency>
```

## O que entrega

| Peça | Comportamento | Configuração |
|---|---|---|
| Resource server JWT | Todo caminho exige JWT válido do Keycloak, menos os públicos. API sem sessão e sem CSRF. `@PreAuthorize` habilitado | `spring.security.oauth2.resourceserver.jwt.issuer-uri` (ou `jwk-set-uri`); `fithub.security.public-paths` (padrão: health do Actuator e OpenAPI) |
| Papéis | `realm_access.roles` do token vira `ROLE_*` em maiúsculas (`personal` → `ROLE_PERSONAL`); claim ausente ou malformada não concede nada | — |
| Problem Details (RFC 9457) | 401 e 403 do filtro de segurança, erros do Spring MVC, validação (com `errors[]` por campo) e 500 genérico, sem vazar mensagem de exceção | um `ResponseEntityExceptionHandler` próprio substitui o padrão |
| Logging JSON | Console em JSON (formato ECS), com `traceId` e `spanId` do Micrometer Tracing (ponte OpenTelemetry, sem exportador) | `logging.structured.format.console` (padrão `ecs`); `management.tracing.sampling.probability` (padrão `1.0`) |

Os padrões de logging entram com a menor precedência: qualquer `application.yml` ou variável de
ambiente do serviço sobrescreve.

A verificação de vínculo ativo entre profissional e cliente **não** fica aqui: ela depende da
projeção local de cada serviço (AGENTS.md).

## Build

Exige JDK 25. Na raiz: `mvn -B verify`. Sem JDK 25 local, dá para rodar no container:

```sh
docker run --rm -v "$PWD:/ws" -w /ws maven:3.9.16-eclipse-temurin-25 mvn -B verify
```
