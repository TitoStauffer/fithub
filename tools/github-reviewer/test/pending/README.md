# Testes pendentes

Testes de contrato dos workflows do CashIn que ainda não existem no FitHub.
Ficam fora do `npm test` (o glob é `test/*.test.mjs`) até a
**DEV-0118** (workflows de orquestração), que deve adaptá-los e trazê-los de
volta para `test/`, ou removê-los se o workflow equivalente do FitHub for
diferente.

| Arquivo | Workflow que verifica |
|---|---|
| `workflow-contract.test.mjs` | `merge-readiness`, `pr-review-signal`, `pr-title`, `ci`, `agent-orchestration`, `agent-report-recovery` |
| `pr-assignee.test.mjs` | `pr-assignee` do CashIn (no FitHub a atribuição está em `project-status.yml`) |

Também estão marcados com `skip: 'DEV-0118: ...'` dois testes de
`test/agent-ci-evidence.test.mjs` que leem `ci.yml`, `agent-orchestration.yml`
e `merge-readiness.yml`.
