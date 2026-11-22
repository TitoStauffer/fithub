# Revisão automatizada (bots de Code Review e QA)

**Última revisão:** 22/11/2026

Como os bots decidem o que revisar e quando o PR pode ser integrado. As regras de
trabalho estão no `AGENTS.md` (seções **Trilho de revisão**, **Processo de Code
Review**, **Processo de QA** e **Merge Readiness**); esta nota aponta para o código
que as implementa, para que agentes e pessoas confirmem a regra na fonte.

## Política do repositório

O que difere do CashIn, de onde o reviewer veio, fica num único módulo: checks
exigidos (`GitHub Reviewer` e o status `PR title`), ausência de SonarQube e de
proteção de branch, raiz do vault, snapshots de CI e checks voláteis excluídos da
evidência.

- Fonte: [repository-policy.ts](../../tools/github-reviewer/src/repository-policy.ts)

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
  monta o diff a partir da lista de arquivos, com o patch de cada arquivo alterado.

- Fonte: [agent-review-inputs.ts](../../tools/github-reviewer/src/agent-review-inputs.ts) —
  `readPullRequestDiff`, `synthesizeDiffFromFiles` e `MAX_SOURCE_FILES`.

## Contexto dos agentes

Os agentes recebem os arquivos alterados, este vault (índice, estado atual e as
notas que o `context-map.json` liga aos caminhos alterados) e os arquivos que essas
notas citam. **Para o bot confirmar uma regra que está no código, a nota que trata
dela precisa citar o arquivo**, como esta faz.

- Fonte: [vault-context.ts](../../tools/github-reviewer/src/vault-context.ts)
