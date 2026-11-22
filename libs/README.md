# libs

Bibliotecas Java **só técnicas**, compartilhadas pelos serviços. Domínio compartilhado é proibido (AGENTS.md).

- [`java-commons`](java-commons/README.md) — resource server JWT, Problem Details e logging JSON com traceId (DEV-0013); outbox transacional e consumidor idempotente (DEV-0014)
- [`event-contracts`](event-contracts/README.md) — JSON Schema dos eventos entre serviços, validação nos testes de produtor e consumidor e regra de versionamento (DEV-0018). Usada com escopo test
