# Estado atual

**Última revisão:** 22/11/2026

## Existe

- **Documentação oficial** em `docs/00-oficial`: [PRD](PRD.md), [ENGENHARIA](ENGENHARIA.md),
  [BACKLOG](BACKLOG.md) e [aprendizados](aprendizados/README.md).
- **Regras de trabalho** no `AGENTS.md` (chave `DEV-NNNN`, branch, commit, PR, merge, papéis,
  Code Review, QA, débitos técnicos).
- **Automação do GitHub** (épico W0):
  - `issue-key`: numera toda issue nova com `[DEV-NNNN] - `.
  - `pr-title`: valida título, branch e issue do PR.
  - `project-status`: move as issues no quadro e atribui o autor ao PR.
  - `ci` (`CI`, DEV-0016): `Detect changes` decide as áreas pelo diff; `GitHub Reviewer` (reviewer, docs, scripts de CI, hook `pre-push`, scripts do runner), `Java Services` (`mvn verify` e guarda de teste pulado), `Apps` (lint, typecheck e testes do workspace pnpm) e `Local Infrastructure` (compose saudável). Os quatro são checks exigidos pelos bots e sempre rodam; área sem mudança termina com sucesso sem trabalhar. `Publish Images` (DEV-0017), só no push na master: imagem de todos os serviços no GHCR com a tag do commit (idempotente); no PR, o `Java Services` constrói a imagem dos serviços alterados e barra imagem que rode como root.
- **Hook local `.githooks/pre-push`** (DEV-0130): recusa push direto para `master`/`main`; ativação por
  clone com `git config core.hooksPath .githooks`.
  - `github-app-check`: verifica os secrets do GitHub App.
  - `approval-reset`: desmarca as aprovações dos agentes do HEAD anterior quando entra commit novo.
  - `agent-orchestration`, `merge-readiness`, `pr-review-signal`, `agent-report-recovery`: bots de
    Code Review, QA, Coordinator e Merge Readiness (DEV-0118). Validação ponta a ponta na DEV-0009.
- **Reviewer** em `tools/github-reviewer`, portado do CashIn e adaptado: sem SonarQube, sem
  proteção de branch, checks do FitHub (`src/repository-policy.ts`), vault nesta pasta. Resume
  `package-lock.json` e `pnpm-lock.yaml` em vez de mandá-los crus ao modelo (DEV-0142).
- **Runner self-hosted** `fithub-agents-windows` na máquina do Tito (`tools/github-runner`).
- **Código da primeira versão** em `legacy/` (monólito modular), só como referência.
- **Documentação da primeira versão** removida da árvore (DEV-0010); recuperável pelo histórico do git
  conforme [docs/HISTORICO.md](../HISTORICO.md). O CI roda [tools/repo-checks/check-docs.mjs](../../tools/repo-checks/check-docs.mjs): caminhos versionados até 200 caracteres, links relativos dos `.md` (fora de `legacy/`) e os caminhos do `HISTORICO.md` presentes no commit citado e ausentes do HEAD.

- **Estrutura do monorepo** (DEV-0011): `services/` (4 serviços), `libs/`, `apps/`, `packages/`, `infra/`
  só com READMEs; `pom.xml` parent (Java 25, Spring Boot 4.1.3, enforcer) e workspace pnpm.
- **Infraestrutura local** (DEV-0012): `infra/compose/docker-compose.dev.yml` com Postgres 17 (um banco e um usuário por serviço, mais o do Keycloak), RabbitMQ, Keycloak, SeaweedFS (S3, ADR-006) e Mailpit, todos com healthcheck; o CI sobe o compose e espera todos saudáveis. O `infrastructure/` legado foi removido
- **java-commons** (DEV-0013): primeiro módulo Maven ([libs/java-commons](../../libs/java-commons/README.md)). Autoconfiguração de resource server JWT do Keycloak (papéis do realm como `ROLE_*`, `@PreAuthorize`), erros em Problem Details (RFC 9457) e logging JSON (ECS) com `traceId`. ArchUnit garante que a lib não tem domínio. O CI roda `mvn -B verify` na raiz
- **Mensageria na java-commons** (DEV-0014, ADR-003): outbox transacional (`OutboxWriter` recusa gravar fora de transação e gera ids UUID v7; `OutboxPublisher` publica só o que foi commitado, na ordem de gravação, com um único publicador por banco via lock consultivo, confirmação do broker e expurgo por retenção) e consumo idempotente (`ProcessedEvents`). As tabelas técnicas usam um Flyway próprio, com histórico separado; o Flyway dos serviços passa a ter linha de base 0 por padrão. Testes com Testcontainers (Postgres e RabbitMQ)
- **Template de serviço e `account-service`** (DEV-0015): `services/account-service` com `domain` / `application` / `infrastructure` / `interfaces` sobre a `java-commons`, Spring MVC com virtual threads, JPA (`ddl-auto: validate`), Flyway com `V1__inicial.sql` (sem tabela de negócio), Actuator (só `health`, com probes) e springdoc (`/v3/api-docs`, sem Swagger UI); exchange `fithub.account`, porta 8081. A [libs/java-testing](../../libs/java-testing/README.md) (escopo de teste) traz as regras ArchUnit, testadas contra serviços de exemplo com violações, e o contrato de esqueleto que o serviço roda contra Postgres e RabbitMQ reais. `client` (porta 8082), `training` (8083) e `nutrition` (8084) receberam o mesmo template na DEV-0141, com exchanges `fithub.client`, `fithub.training` e `fithub.nutrition`
- **Contratos de evento** (DEV-0018): [libs/event-contracts](../../libs/event-contracts/README.md) com JSON Schema (draft 2020-12, fechado) dos seis eventos iniciais da ENGENHARIA §3, com routing keys `<origem>.<evento>.v1` (`client.link-activated.v1`, `client.link-ended.v1`, `client.client-updated.v1`, `client.account-deleted.v1`, `account.professional-registered.v1`, `account.subscription-changed.v1`), e `EventContracts` para validar payload nos testes de produtor e consumidor (formatos verificados). Regra de versionamento no README. Nenhum serviço publica nem consome esses eventos ainda (E1 em diante)
- **App web** (DEV-0020): [apps/web](../../apps/web/README.md), o esqueleto do app do profissional com React 19, Vite 8, TypeScript 6.0 (limite do typescript-eslint), TanStack Query, shadcn/ui (Tailwind 4), ESLint sem avisos, Prettier e Vitest com React Testing Library. Tem um componente de exemplo (status do account-service) com testes reais. O job `Apps` do CI roda lint, typecheck, testes, build e a guarda de teste pulado, que passou a exigir relatório
- **App mobile** (DEV-0021): [apps/mobile](../../apps/mobile/README.md), o esqueleto do app do cliente com Expo SDK 58 (React Native 0.88, React 19.3), TypeScript 6.0, ESLint 9 com `eslint-config-expo` (a configuração do Expo ainda não suporta o ESLint 10) e Jest com jest-expo e React Native Testing Library. Tem um componente de exemplo (status do client-service) com testes reais e JUnit para a guarda do CI. Os `package.json` dos dois apps entram nos snapshots de CI dos bots

## Ainda não existe

- Regras de negócio, endpoints e tabelas dos serviços — épicos E1 em diante. Apps e demais bibliotecas — resto do E0.
- Verificação por mutação no CI — DEV-0116. Deploy na VPS — épico de operação.

## Limitações conhecidas

- Repositório privado no plano gratuito: sem proteção de branch e sem SonarQube. A regra
  "merge só com `READY_TO_MERGE`" é escrita no `AGENTS.md`.
