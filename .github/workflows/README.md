# Workflows

| Workflow | Quando roda | O que faz |
|---|---|---|
| `backend.yml` | push e PR | Placeholder do CI antigo (só `echo`). Substituído pelo CI real na DEV-0016 |
| `issue-key.yml` | issue aberta; manual | Dá a próxima chave `[DEV-NNNN] - ` ao título da issue (DEV-0004) |
| `pr-title.yml` | PR aberto, editado ou atualizado | Valida título, branch e issue vinculada com a mesma chave e publica o status `PR title` (DEV-0005) |

Regras gerais (AGENTS.md):

- Workflows com permissão de escrita nunca fazem checkout nem executam código
  de PR ou conteúdo de issue; dados do evento entram só por variável de
  ambiente.
- Permissões declaradas por job (`permissions: {}` no topo).
