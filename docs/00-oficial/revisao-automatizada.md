# Revisão automatizada (bots de Code Review e QA)

**Última revisão:** 22/11/2026 (DEV-0016)

Como os bots decidem o que revisar e quando o PR pode ser integrado. As regras de
trabalho estão no `AGENTS.md` (seções **Trilho de revisão**, **Processo de Code
Review**, **Processo de QA** e **Merge Readiness**); esta nota aponta para o código
que as implementa, para que agentes e pessoas confirmem a regra na fonte.

## Política do repositório

O que difere do CashIn, de onde o reviewer veio, fica num único módulo: checks
exigidos, ausência de SonarQube e de proteção de branch, raiz do vault, snapshots
de CI e checks voláteis excluídos da evidência.

- Fonte: [repository-policy.ts](../../tools/github-reviewer/src/repository-policy.ts)

### Checks exigidos (DEV-0016)

O workflow `CI` tem um job por área, e os quatro são exigidos no HEAD, vindos do
app das GitHub Actions: `GitHub Reviewer`, `Java Services`, `Apps` e
`Local Infrastructure` (`REQUIRED_AGENT_CHECKS`, `REQUIRED_CI_JOBS`). O status
`PR title` também é exigido. Os mesmos quatro jobs são os `KNOWN_TEST_JOBS`, cujas
etapas verificadas vão para o QA.

- O job `Detect changes` roda
  [ci-changes.mjs](../../tools/repo-checks/ci-changes.mjs) e decide as áreas pelo
  diff do PR. O diff é lido com `--no-renames -z`, para que arquivo movido e nome
  com acento não escapem da área (aprendizado `diff-por-caminho-sem-renomeacao`).
  Push na master roda tudo, e mudança no próprio CI ou em `tools/repo-checks/`
  também.
- Cada job de área **sempre roda**. Quando a área não mudou, o passo `Skip` termina
  com sucesso e os passos de verdade aparecem como pulados. Se o detector falhar ou
  não disser `false`, o job fica sem sucesso, e o merge bloqueia. Job pulado
  inteiro (`skipped`) nunca conta como sucesso.
- **Mutação (DEV-0116):** o `Detect changes` publica os manifestos `mutations/<CHAVE>.json`
  alterados no PR e liga as áreas que eles citam. O `GitHub Reviewer`, o `Java Services` e o `Apps`
  rodam [mutations.mjs](../../tools/repo-checks/mutations.mjs) com os mutantes da sua área (`tools`,
  `java`, `apps`). Cada comando roda antes sem mutante e precisa passar. Mutante que sobrevive, comando
  que já falha ou não executa, trecho não único ou arquivo não restaurado derruba o job.
  Formato no ENGENHARIA.md §9.
- Os jobs `Java Services` e `Apps` rodam a guarda contra teste pulado
  ([check-skipped-tests.mjs](../../tools/repo-checks/check-skipped-tests.mjs)) nos
  relatórios JUnit. Teste pulado precisa de `DEV-xxxx` na justificativa, e sem
  nenhum teste a guarda falha. O JUnit do Vitest não grava motivo, então nos apps
  a issue vai no nome do teste: `it.skip('motivo DEV-xxxx', ...)`. Desde a DEV-0020
  o `Apps` exige relatório (o `apps/web` publica em `reports/junit.xml`) e roda
  lint, typecheck, testes e build.
- Snapshots de CI (`CI_SNAPSHOT_PATHS`):
  - o `ci.yml`, sem os valores de `env`;
  - os `scripts` do `package.json` do reviewer e dos apps `web` e `mobile` (DEV-0021);
  - os dois scripts acima, inteiros.

  Arquivo sem projeção conhecida falha fechado (`projectCiSnapshot`).
- Testes: `requires each per-area CI job from GitHub Actions on the current HEAD`
  ([agent-review-inputs.test.mjs](../../tools/github-reviewer/test/agent-review-inputs.test.mjs)),
  `requires every per-area CI job from GitHub Actions on the current HEAD`
  ([merge-readiness.test.mjs](../../tools/github-reviewer/test/merge-readiness.test.mjs)),
  `coordinator never approves a summary that lacks a per-area CI job`
  ([agent-stage-policy.test.mjs](../../tools/github-reviewer/test/agent-stage-policy.test.mjs))
  e [ci-scripts.test.mjs](../../tools/repo-checks/ci-scripts.test.mjs).

## Trilho de revisão

O trilho é **leve** só quando todo arquivo alterado é inerte: `*.md`, testes
(`*.test.{ts,tsx,js,jsx,mjs,cjs}`, `*Test.java`, `*IT.java`) ou o mapa do vault
`docs/00-oficial/context-map.json` (decisão do Tito na DEV-0133). Qualquer outro
caminho exige o trilho **completo**. O leve dispensa só o QA.

- Fonte: [review-track.ts](../../tools/github-reviewer/src/review-track.ts) —
  `LIGHT_TRACK_EXACT`, `TEST_FILE` e `LIGHT_TRACK_RULE`.
- Testes: [review-track.test.mjs](../../tools/github-reviewer/test/review-track.test.mjs) —
  inclusive `the FitHub vault context map takes the light track and is named in the published rule`.

## Merge Readiness

Publica `READY_TO_MERGE`, `BLOCKED` ou `INCONCLUSIVE` no HEAD. Sem proteção de
branch disponível no plano, não consulta nem exige branch protection e diz isso
nas evidências.

- Fonte: [merge-readiness.ts](../../tools/github-reviewer/src/merge-readiness.ts)

## Tamanho do PR

- Até **32 arquivos novos ou alterados** por PR têm o conteúdo completo no contexto;
  acima disso o Code Review falha fechado (`INCONCLUSIVE`): quebre o PR.
- **Arquivos removidos** não contam nesse limite quando ele seria excedido: entram só
  pelo caminho e pelo número de linhas removidas (DEV-0137).
- Acima de **300 arquivos**, o GitHub não entrega o diff completo (erro 406); o reviewer
  monta o diff a partir da lista de arquivos, com o patch de cada arquivo alterado; os removidos
  entram num bloco inicial, uma linha por caminho (DEV-0138).
- O diff entregue ao modelo tem teto de **350.000 caracteres**; acima disso o estágio falha fechado.
- A cobertura do Code Review (`changedFilesReviewed`) aceita até **200 arquivos**. Acima disso, os
  removidos saem da matriz de cobertura e os novos e alterados continuam obrigatórios. O trilho
  segue classificado pela lista completa, removidos incluídos (DEV-0140, `selectReviewCoverageFiles`).
- A lista de arquivos do GitHub para em **3000 arquivos**; se ela não bater com o total que o
  PR informa (`changed_files`), a revisão falha fechado em vez de revisar só parte do PR.
- **Lockfiles** (`package-lock.json` e `pnpm-lock.yaml`, na raiz ou em subpasta) não entram crus:
  - no diff, a seção vira um marcador com tamanho e SHA-256;
  - no contexto de fonte, entra um resumo determinístico com o total de pacotes, os resolvidos fora
    do registry oficial (tarball, git, diretório) e os sem hash de integridade, todos como literais JSON;
  - o `pnpm-lock.yaml` é lido por linha, só no formato 9; outra versão falha fechado (DEV-0142).

  Que o lockfile bate com os manifestos, quem garante é o `install --frozen-lockfile` do CI, não o modelo.

- Fonte: [agent-review-inputs.ts](../../tools/github-reviewer/src/agent-review-inputs.ts) —
  `readPullRequestDiff`, `synthesizeDiffFromFiles`, `MAX_SOURCE_FILES`, `summarizeNpmLockfile` e
  `summarizePnpmLockfile`; teto do diff em
  [agent-stage-cli.ts](../../tools/github-reviewer/src/agent-stage-cli.ts).

## Contexto dos agentes

Os agentes recebem os arquivos alterados, este vault (índice, estado atual e as
notas que o `context-map.json` liga aos caminhos alterados) e os arquivos que essas
notas citam. **Para o bot confirmar uma regra que está no código, a nota que trata
dela precisa citar o arquivo**, como esta faz.

- Fonte: [vault-context.ts](../../tools/github-reviewer/src/vault-context.ts)
