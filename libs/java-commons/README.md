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
| Problem Details (RFC 9457) | 401 e 403 do filtro de segurança, erros do Spring MVC, validação (com `errors[]` por campo) e 500 genérico, sem vazar mensagem de exceção. No log, o erro de banco vai só com o tipo, o SQLState e a constraint, sem a linha recusada nem a chave (DEV-0150) | um `ResponseEntityExceptionHandler` próprio substitui o padrão |
| Logging JSON | Console e arquivo de log em JSON (formato ECS), com `traceId` e `spanId` do Micrometer Tracing (ponte OpenTelemetry). O arquivo só existe com `logging.file.name`; é o que o coletor do compose lê (DEV-0109) | `logging.structured.format.console` e `logging.structured.format.file` (padrão `ecs`); `management.tracing.sampling.probability` (padrão `1.0`) |
| Métricas de sucesso (DEV-0113) | `AggregateGauges.register(nome, descrição, rótulos, consulta)`: medidor `fithub.*` com a contagem que o serviço calcula no próprio banco a cada intervalo; a exportação lê o último valor. Só aceita os rótulos `type` e `status`, nunca um id. Uma consulta que falha mantém o último valor | `fithub.metrics.refresh-interval` (padrão 5 min) |
| Exportação das métricas por OTLP (DEV-0113) | Registro OTLP oficial do Micrometer. **Desligada por padrão.** Ligada, exporta todos os medidores do serviço, não só os `fithub.*`: os padrões do Spring (JVM, `http.server.requests` com o modelo da rota, não o caminho real, e o pool do banco), sem dado de pessoa. **Medidor novo**, de qualquer biblioteca, segue a mesma regra das métricas agregadas: nenhum rótulo com id, e-mail, caminho real ou dado de saúde (Code Review do PR #326) | `management.otlp.metrics.export.enabled` (padrão `false`) e `management.otlp.metrics.export.url`, por exemplo `http://localhost:4318/v1/metrics` |
| Traces por OTLP (DEV-0109, [ADR-013](../../docs/00-oficial/adr/ADR-013-observabilidade.md)) | Exportador OTLP/HTTP oficial do OpenTelemetry. **Sem endpoint, nada é exportado.** As observações do Spring Security ficam desligadas (`management.observations.enable.spring.security=false`): os spans delas levavam os papéis da pessoa | `management.opentelemetry.tracing.export.otlp.endpoint`, por exemplo `http://localhost:4318/v1/traces` (variável `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`) |

Os padrões de logging entram com a menor precedência: qualquer `application.yml` ou variável de
ambiente do serviço sobrescreve.

## Mensageria (DEV-0014, ADR-003)

| Peça | Comportamento | Configuração |
|---|---|---|
| Tabelas técnicas | `fithub_outbox_event` (com `sequence` como ordem de gravação) e `fithub_processed_event`, aplicadas por um Flyway próprio (`db/fithub-commons`, histórico em `flyway_fithub_commons_history`) que não colide com a numeração do serviço | — |
| `OutboxWriter.append(routingKey, payload)` | Grava o evento (JSON) na transação do dado, com o `traceparent` W3C do span atual (DEV-0109); **fora de transação é recusado**. Devolve o id do evento (UUID v7) | `fithub.messaging.exchange` (exchange `topic` do serviço, ex.: `fithub.client`), declarada automaticamente |
| `FithubMessagingProperties.sourceExchange(nome)` | A exchange de outro serviço que o consumidor liga às próprias filas. Só é declarada com `declare-source-exchanges`; em produção quem a declara é o administrador do broker (DEV-0177) | `fithub.messaging.declare-source-exchanges` (padrão `true`; `false` em produção e nos testes dos serviços) |
| `OutboxPublisher` | Publica os pendentes já commitados (rollback nunca vira mensagem), **na ordem de gravação**, com `messageId` = id do evento, o cabeçalho `traceparent` gravado pelo `OutboxWriter` (DEV-0109) quando a gravação aconteceu dentro de um trace e confirmação do broker antes de marcar como publicado. Um único publicador por banco de serviço, mesmo com várias instâncias (lock consultivo do Postgres). Na falha, registra `attempts`/`last_error` e para o lote; o próximo ciclo recomeça do mesmo evento. Expurga publicados e consumos além da retenção | `fithub.messaging.publisher.{enabled, poll-interval, batch-size, confirm-timeout, outbox-retention, processed-retention, purge-interval}` (padrões `true`, `1s`, `100`, `5s`, `7d`, `30d`, `1h`) |
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
  migração. Com a base em 0, a V1 do serviço ainda roda;
- `spring.rabbitmq.listener.simple.observation-enabled=true` (DEV-0109): o listener lê o cabeçalho
  `traceparent` e continua o trace da requisição que gravou o evento. O publicador não abre span
  próprio; o evento gravado fora de um trace vai sem o cabeçalho.

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
