# Workflows

| Workflow | Quando roda | O que faz |
|---|---|---|
| `ci.yml` (`CI`) | push na master e PR | Job `GitHub Reviewer`: build e testes de `tools/github-reviewer`. Jobs dos serviços e apps entram na DEV-0016 (DEV-0007) |
| `issue-key.yml` | issue aberta; manual | Dá a próxima chave `[DEV-NNNN] - ` ao título da issue (DEV-0004) |
| `pr-title.yml` | PR aberto, editado ou atualizado | Valida título, branch e issue vinculada com a mesma chave e publica o status `PR title` (DEV-0005) |
| `project-status.yml` | issue aberta, branch criada, PR aberto/rascunho/fechado, review pedindo ajustes | Move a issue no quadro e atribui o autor ao PR (DEV-0006) |

Secrets e variáveis:

| Nome | Tipo | Para quê |
|---|---|---|
| `PROJECT_TOKEN` | secret | PAT clássico com escopos `repo` e `project` (o repositório é privado: sem `repo` o token não enxerga a issue). Quadros de conta pessoal não aceitam o `GITHUB_TOKEN`. Sem ele, `project-status` só avisa |
| `PROJECT_NUMBER` | variável (opcional) | Número do quadro; padrão `1` |

Regras gerais (AGENTS.md):

- Workflows com permissão de escrita nunca fazem checkout nem executam código
  de PR ou conteúdo de issue; dados do evento entram só por variável de
  ambiente.
- Permissões declaradas por job (`permissions: {}` no topo).
