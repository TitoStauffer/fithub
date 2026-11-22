# Estado atual

**Última revisão:** 21/11/2026

## Existe

- **Documentação oficial** em `docs/00-oficial`: [PRD](PRD.md), [ENGENHARIA](ENGENHARIA.md),
  [BACKLOG](BACKLOG.md) e [aprendizados](aprendizados/README.md).
- **Regras de trabalho** no `AGENTS.md` (chave `DEV-NNNN`, branch, commit, PR, merge, papéis,
  Code Review, QA, débitos técnicos).
- **Automação do GitHub** (épico W0):
  - `issue-key`: numera toda issue nova com `[DEV-NNNN] - `.
  - `pr-title`: valida título, branch e issue do PR.
  - `project-status`: move as issues no quadro e atribui o autor ao PR.
  - `ci` (`CI`): job `GitHub Reviewer` (testes do reviewer e dos scripts do runner).
  - `github-app-check`: verifica os secrets do GitHub App.
  - `agent-orchestration`, `merge-readiness`, `pr-review-signal`, `agent-report-recovery`: bots de
    Code Review, QA, Coordinator e Merge Readiness (DEV-0118). Validados ponta a ponta no PR #138
    (DEV-0009): Code Review e QA aprovados pelo GitHub App, Coordinator aprovado e `READY_TO_MERGE`.
- **Reviewer** em `tools/github-reviewer`, portado do CashIn e adaptado: sem SonarQube, sem
  proteção de branch, checks do FitHub (`src/repository-policy.ts`), vault nesta pasta.
- **Runner self-hosted** `fithub-agents-windows` na máquina do Tito (`tools/github-runner`).
- **Código da primeira versão** em `legacy/` (monólito modular), só como referência.

## Ainda não existe

- Os microsserviços (`services/`), apps (`apps/web`, `apps/mobile`), bibliotecas (`libs/`) e
  infraestrutura local (`infra/`) — épico E0 em diante.
- CI dos serviços e apps — DEV-0016. Verificação por mutação no CI — DEV-0116.

## Limitações conhecidas

- Repositório privado no plano gratuito: sem proteção de branch e sem SonarQube. A regra
  "merge só com `READY_TO_MERGE`" é escrita no `AGENTS.md`.
