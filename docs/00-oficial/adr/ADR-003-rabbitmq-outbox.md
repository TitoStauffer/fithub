# ADR-003 — RabbitMQ com outbox transacional

**Status:** aceito · **Data:** 21/11/2026 · **Decisor:** Tito

## Contexto

Os serviços precisam reagir a mudanças uns dos outros (ex.: vínculo encerrado corta o acesso do
profissional em `training` e `nutrition`) sem chamadas síncronas (ADR-001).

## Decisão

RabbitMQ como broker (exchange `topic` por serviço de origem), publicação pelo **outbox
transacional** (o evento é gravado na mesma transação do dado) e **consumidores idempotentes**
(tabela de eventos processados). Contratos em JSON Schema versionados (`libs/event-contracts`).
Cada serviço mantém projeções locais mínimas do que precisa dos outros.

## Alternativas consideradas

- **Kafka:** log durável e replay, mas pesado de operar e caro para o volume inicial.
- **Só HTTP síncrono:** mais simples, mas um serviço fora derruba os outros.

## Consequências

- Consistência eventual: acesso liberado ou cortado com atraso de segundos; corte de acesso tratado
  com prioridade e coberto por testes de integração.
- Outbox e consumidor idempotente ficam em `libs/java-commons` (DEV-0014).
