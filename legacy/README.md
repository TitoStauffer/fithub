# Legado (v0.4.0)

Primeira versão do FitHub, construída a partir da especificação anterior
(`MVP_SPECIFICATION.md`, hoje no histórico do git — ver
[docs/HISTORICO.md](../docs/HISTORICO.md)): backend em monólito modular (identity, patients,
training, nutrition) e frontend web em React (JavaScript).

**Este código não é mais evoluído.** Ele serve de referência para portar
agregados, regras e testes para os novos microsserviços, conforme
[docs/00-oficial/ENGENHARIA.md](../docs/00-oficial/ENGENHARIA.md), seção 6.

O que vale levar:

- Agregados e value objects de `training`, `nutrition` e `patients`, com seus
  testes unitários (que passam).
- Migrations Flyway como ponto de partida para os bancos de cada serviço.

O que não vale:

- O módulo `identity` (substituído pelo Keycloak).
- Os testes de integração em `backend/boot/src/test`, que são placeholders
  (`assertTrue(true)`) e não testam nada, apesar do que dizia o `IMPROVEMENTS.md` da primeira versão (ver [docs/HISTORICO.md](../docs/HISTORICO.md)).
