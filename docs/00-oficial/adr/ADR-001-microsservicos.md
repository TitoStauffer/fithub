# ADR-001 — Microsserviços independentes desde o MVP

**Status:** aceito · **Data:** 21/11/2026 · **Decisor:** Tito

## Contexto

O FitHub tem três lados que o cliente contrata de forma independente (personal, nutricionista,
cliente). O Tito quer cada lado como serviço com responsabilidade própria, que possa evoluir e ser
implantado separadamente. O time é um dev solo com Claude Code.

## Decisão

Quatro microsserviços desde o MVP — `account`, `client`, `training`, `nutrition` —, cada um com o
próprio banco PostgreSQL (lógico) e comunicação por eventos (ADR-003), sem chamadas síncronas entre
serviços no caminho principal. Detalhe em [ENGENHARIA.md §2–3](../ENGENHARIA.md).

## Alternativas consideradas

- **Monólito modular primeiro** (recomendado na discussão): um deploy, módulos com fronteiras
  rígidas, extração posterior. Mais barato para dev solo. Não escolhido pelo Tito.
- **Microsserviços com chamadas HTTP síncronas:** mais simples no início, mas acopla a
  disponibilidade dos serviços.

## Consequências

- Custo maior por feature: contratos de evento, outbox, idempotência, consistência eventual e
  deploy de vários processos.
- Mitigações: só quatro serviços, bibliotecas técnicas compartilhadas (`libs/java-commons`),
  compose local com um comando (DEV-0012) e CI por caminho (DEV-0016).
- Domínio compartilhado entre serviços é proibido (AGENTS.md).
