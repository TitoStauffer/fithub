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

## Mensageria (DEV-0014, ADR-003)

| Peça | Comportamento | Configuração |
|---|---|---|
| Tabelas técnicas | `fithub_outbox_event` (com `sequence` como ordem de gravação) e `fithub_processed_event`, aplicadas por um Flyway próprio (`db/fithub-commons`, histórico em `flyway_fithub_commons_history`) que não colide com a numeração do serviço | — |
| `OutboxWriter.append(routingKey, payload)` | Grava o evento (JSON) na transação do dado; **fora de transação é recusado**. Devolve o id do evento (UUID v7) | `fithub.messaging.exchange` (exchange `topic` do serviço, ex.: `fithub.client`), declarada automaticamente |
| `OutboxPublisher` | Publica os pendentes já commitados (rollback nunca vira mensagem), **na ordem de gravação**, com `messageId` = id do evento e confirmação do broker antes de marcar como publicado. Um único publicador por banco de serviço, mesmo com várias instâncias (lock consultivo do Postgres). Na falha, registra `attempts`/`last_error` e para o lote; o próximo ciclo recomeça do mesmo evento. Expurga publicados e consumos além da retenção | `fithub.messaging.publisher.{enabled, poll-interval, batch-size, confirm-timeout, outbox-retention, processed-retention, purge-interval}` (padrões `true`, `1s`, `100`, `5s`, `7d`, `30d`, `1h`) |
| `ProcessedEvents.processOnce(consumer, eventId \| message, handler)` | Roda o handler uma vez por evento e consumidor, na mesma transação do registro; falha do handler desfaz o registro e a reentrega processa | — |

**Cada consumidor declara a própria fila durável, ligada à exchange de origem, antes de o produtor
emitir.** Mensagem sem fila ligada é confirmada pelo broker e descartada; o publicador não usa
`mandatory`.

Ids gerados pela lib seguem a convenção UUID v7 (`com.fithub.commons.ids.UuidV7`, RFC 9562).

A entrega é **pelo menos uma vez**: um evento confirmado pelo broker pode ser reenviado se o commit
da marcação falhar, e o consumo idempotente absorve a repetição.

Padrões que a biblioteca define com a menor precedência:
- `spring.rabbitmq.publisher-confirm-type=simple`, exigido pela confirmação do publicador;
- `spring.flyway.baseline-on-migrate=true` e `spring.flyway.baseline-version=0`. As tabelas da
  biblioteca deixam o schema não vazio, e sem esses padrões o Flyway do serviço recusaria a primeira
  migração. Com a base em 0, a V1 do serviço ainda roda.

A verificação de vínculo ativo entre profissional e cliente **não** fica aqui: ela depende da
projeção local de cada serviço (AGENTS.md).

## Build

Exige JDK 25. Na raiz: `mvn -B verify`. Sem JDK 25 local, dá para rodar no container:

```sh
docker run --rm -v "$PWD:/ws" -w /ws \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3.9.16-eclipse-temurin-25 mvn -B verify
```

Os testes de mensageria usam Testcontainers (Postgres 17.12 e RabbitMQ 4.3.8), por isso o socket do
Docker entra no comando.
