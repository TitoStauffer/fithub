# Workflows

| Workflow | Quando roda | O que faz |
|---|---|---|
| `ci.yml` (`CI`) | push na master e PR | Job `GitHub Reviewer`: build e testes de `tools/github-reviewer`, teste do hook `pre-push` e dos scripts do runner. Jobs dos serviços e apps entram na DEV-0016 (DEV-0007) |
| `issue-key.yml` | issue aberta; manual | Dá a próxima chave `[DEV-NNNN] - ` ao título da issue (DEV-0004) |
| `pr-title.yml` | PR aberto, editado ou atualizado | Valida título, branch e issue vinculada com a mesma chave e publica o status `PR title` (DEV-0005) |
| `agent-orchestration.yml` | CI concluído com sucesso num PR; manual | Code Review → QA (trilho completo) → Quality Gate Coordinator, no runner `fithub-agents`; publica revisão formal e relatórios pelo GitHub App (DEV-0118) |
| `merge-readiness.yml` | CI, sinal de revisão, relatório do Coordinator; manual | Publica o status `Merge Readiness` (`READY_TO_MERGE` / `BLOCKED` / `INCONCLUSIVE`) (DEV-0118) |
| `pr-review-signal.yml` | revisão formal enviada/editada/descartada | Só sinaliza para o Merge Readiness reavaliar; sem permissões (DEV-0118) |
| `agent-report-recovery.yml` | manual | Republica um resultado de agente já produzido, sem nova chamada ao modelo (DEV-0118) |
| `github-app-check.yml` | manual; PR que o altera | Verifica os secrets do GitHub App sem escrever nada (DEV-0008) |
| `project-status.yml` | issue aberta, branch criada, PR aberto/rascunho/fechado, review pedindo ajustes | Move a issue no quadro e atribui o autor ao PR (DEV-0006) |

Secrets e variáveis:

| Nome | Tipo | Para quê |
|---|---|---|
| `PROJECT_TOKEN` | secret | PAT clássico com escopos `repo` e `project` (o repositório é privado: sem `repo` o token não enxerga a issue). Quadros de conta pessoal não aceitam o `GITHUB_TOKEN`. Sem ele, `project-status` só avisa |
| `FITHUB_APP_ID`, `FITHUB_APP_INSTALLATION_ID`, `FITHUB_APP_PRIVATE_KEY` | secrets | GitHub App que publica revisões e relatórios dos agentes |
| `FITHUB_APP_BOT_LOGIN` | variável | Login do bot do App (`titostauffer-devagents[bot]`) |
| `PROJECT_NUMBER` | variável (opcional) | Número do quadro; padrão `1` |

Regras gerais (AGENTS.md):

- Workflows com permissão de escrita nunca fazem checkout nem executam código
  de PR ou conteúdo de issue; dados do evento entram só por variável de
  ambiente.
- Permissões declaradas por job (`permissions: {}` no topo).
