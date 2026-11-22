# Aprendizados — automação do GitHub (workflows, runner, bots)

Bugs reais do épico W0 e como não repeti-los. Formato no [README](README.md).

## concorrencia-pendente-cancelada

**Regra:** num grupo de `concurrency` do GitHub Actions, só existe **uma** execução pendente; a
seguinte substitui e cancela a anterior. Workflow que processa um item por evento perde itens.
Faça cada execução idempotente e varrendo **todo** o trabalho pendente, não só o do evento.

### Ocorrências

- **Revisão:** bug de validação — DEV-0120 (#126) · **PR:** #127 · **SHA:** `7589ca9`
  - **Impacto:** 3 issues abertas juntas; a do meio ficou sem chave DEV.
  - **Correção:** `issue-key.yml` numera todas as issues abertas sem chave a cada execução.
  - **Prevenção:** teste real com 3 issues em sequência rápida.

## token-escopo-por-chamada

**Regra:** um token com escopo restrito (ex.: PAT só para o quadro) é usado **somente** nas chamadas
que precisam dele; leituras do repositório seguem com o `GITHUB_TOKEN`. Repositório privado exige
que o token consiga ler o recurso referenciado (issue) para usá-lo em outra API (quadro): `repo` +
`project` no PAT clássico.

### Ocorrências

- **Revisão:** bug de validação — DEV-0124 (#132) · **PR:** #133 · **SHA:** `8bbfab6`
  - **Impacto:** `project-status` falhou com 404 e depois `Could not resolve to a node`.
  - **Correção:** `GH_TOKEN="${PROJECT_TOKEN}"` só nas chamadas GraphQL do quadro; PAT recriado com
    `repo` e `project`; falha de acesso vira aviso, não PR vermelho.
  - **Prevenção:** documentação do secret em `.github/workflows/README.md`.

## runner-windows-git-msys

**Regra:** no runner self-hosted Windows, o PATH herdado pode trazer o git do MSYS2
(`C:\msys64\usr\bin`) antes do Git for Windows; o git do MSYS2 não aplica a credencial que o
`actions/checkout` grava e cai no prompt de usuário. O runner põe o Git for Windows à frente.

### Ocorrências

- **Revisão:** bug de validação — DEV-0125 (#139), orquestração 44962392217 · **PR:** #140 · **SHA:** `ff02a9d`
  - **Impacto:** `could not read Username for 'https://github.com'` no checkout; nenhum agente rodou.
  - **Correção:** `Set-RunnerEnvironment` coloca `%ProgramFiles%\Git\cmd` logo depois do pwsh.
  - **Prevenção:** teste da ordem do PATH em `test-runner-scripts.ps1`.

## runner-windows-longpaths

**Regra:** Git for Windows respeita o limite de 260 caracteres sem `core.longpaths`. Ligue-o no
ambiente do runner (`GIT_CONFIG_*`), sem alterar a configuração global do usuário.

### Ocorrências

- **Revisão:** bug de validação — DEV-0126 (#141), orquestração 44964790517 · **PR:** #142 · **SHA:** `14813d4`
  - **Impacto:** `Filename too long` em `docs/02-use-cases/...` no checkout.
  - **Correção:** `GIT_CONFIG_COUNT/KEY_0/VALUE_0` com `core.longpaths=true` no processo do runner.
  - **Prevenção:** teste das variáveis em `test-runner-scripts.ps1`.

## evidencia-checks-volateis

**Regra:** os agentes precisam ver todos os checks do HEAD que comprovam critérios, mas checks da
própria automação que reexecutam por evento (`Sincronizar quadro`, `Validate PR metadata`,
`Review event relay`) ficam fora: a reexecução no meio da orquestração muda a impressão digital do
contexto entre as etapas.

### Ocorrências

- **Revisão:** relatório de QA do PR #138 — DEV-0128 (#144) · **PR:** #145 · **SHA:** `16b6b8d`
  - **Impacto:** QA `INCONCLUSIVE` porque o check `Verificar GitHub App` não chegou às evidências.
  - **Correção:** `summarizeCheckRunEvidence` inclui tudo menos `CONTEXT_EXCLUDED_CHECKS`.
  - **Prevenção:** teste de inclusão/exclusão em `agent-review-inputs.test.mjs`.

## powershell-lista-vazia-vira-null

**Regra:** função PowerShell que devolve lista vazia devolve `$null`, e `@($null).Count` é **1**.
Envolva o resultado em `@(...)` ao atribuir (`$x = @(Funcao ...)`).

### Ocorrências

- **Revisão:** teste novo antes do merge — DEV-0008 (#114) · **PR:** #134 · **SHA:** `cfa18ba`
  - **Impacto:** com só o runner irmão (CashIn) rodando, o runner do fithub recusaria iniciar
    (`foreign-listener`). Pego pelo teste novo antes do merge.
  - **Correção:** `$state.Foreign = @(Remove-SiblingListener ...)`.
  - **Prevenção:** teste do listener irmão em `test-runner-scripts.ps1`.

## scripts-shell-precisam-de-lf

**Regra:** com `core.autocrlf=true`, scripts `sh` e hooks recebem CRLF no checkout do Windows e
quebram. Fixe `eol=lf` no `.gitattributes` para `.githooks/*` e `*.sh`.

### Ocorrências

- **Revisão:** aviso do git no commit — DEV-0130 (#147) · **PR:** #148 · **SHA:** `d9e4ca3`
  - **Impacto:** o git avisou que converteria `.githooks/pre-push` para CRLF no próximo checkout
    (`autocrlf=true`); com CRLF o `sh` do hook quebra. Corrigido antes do merge.
  - **Correção:** regras `eol=lf` no `.gitattributes`.
  - **Prevenção:** `git ls-files --eol .githooks` mostra `i/lf w/lf`.


## hook-precisa-modo-executavel

**Regra:** hook ou script que o git executa precisa estar em `100755` no índice. Recolocar o arquivo
no índice (`git rm --cached` + `git add`) no Windows volta o modo para `100644`, e fora do Windows o
git ignora o hook em silêncio. Marque com `git update-index --chmod=+x` e teste o modo.

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/148#pullrequestreview-6009201174 · **PR:** #148 · **SHA:** `d9e4ca3`
  - **Impacto:** HIGH no Code Review — em Linux/macOS o hook não rodaria e o push direto na master passaria.
  - **Correção:** `git update-index --chmod=+x .githooks/pre-push .githooks/test-pre-push.sh`.
  - **Prevenção:** `test-pre-push.sh` falha se `git ls-files -s` não mostrar `100755`.

## bot-so-confirma-o-que-o-vault-cita

**Regra:** o Code Review recebe os arquivos alterados, o vault e os arquivos que as notas do vault
**citam**. Um PR que afirma algo sobre código não alterado (ex.: "o classificador já faz isso") só é
confirmável se uma nota ligada pelo `context-map.json` citar esse código. Sem isso, o resultado é
`INCONCLUSIVE`, não aprovação.

### Ocorrências

- **Revisão:** relatório INCONCLUSIVE do Code Review no PR #153 (duas rodadas) — DEV-0133 (#152) · **PR:** #153
  - **Impacto:** PR correto ficou bloqueado; o bot não tinha `review-track.ts` nem o teste no contexto.
  - **Correção:** nota `docs/00-oficial/revisao-automatizada.md` cita o código do reviewer e entra pela
    rota de `AGENTS.md`/`tools/**` no `context-map.json`.
  - **Prevenção:** regra registrada na própria nota (seção "Contexto dos agentes").

## aprovacao-vale-so-para-o-head

**Regra:** marca de aprovação (checkbox, relatório, revisão) vale para um SHA. Quando entra commit
novo, o que foi marcado para o SHA anterior é desmarcado, sem apagar a aprovação que o agente já deu
ao SHA novo (o reset pode chegar depois dela).

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/138#pullrequestreview-6009185188 — DEV-0132 (#151) · **PR:** #154 · **SHA:** `31666d3`
  - **Impacto:** o PR #138 declarava Code Review e QA aprovados para um HEAD que ainda não tinha sido revisado.
  - **Correção:** workflow `approval-reset` no `synchronize`, preservando a aprovação do bot que já vale
    para o HEAD atual, relendo o PR antes de gravar e paginando as leituras.
  - **Prevenção:** testes das guardas em `test/approval-reset.test.mjs`, cada uma provada por mutação.

## runner-reregister-precisa-limpar-registro-local

**Regra:** `config.cmd --replace` substitui o registro do runner **no GitHub**, mas o `config.cmd` recusa
reconfigurar enquanto o registro local (`.runner`, `.credentials`, `.credentials_rsaparams`) existe.
Reregistrar exige remover esses arquivos antes, com o runner parado. Para só reiniciar ou mover o
runner, rode o instalador sem `-Reregister`.

### Ocorrências

- **Revisão:** bug de uso — reinstalação do runner pelo Tito em 22/11/2026, DEV-0135 (#158) · **PR:** #159 · **SHA:** `01130ab`
  - **Impacto:** `install-runner.ps1 -Reregister` falhava com "Cannot configure the runner because it is already configured".
  - **Correção:** `Clear-RunnerRegistration` antes de `Register-Runner` quando `-Reregister`.
  - **Prevenção:** testes funcional e estático em `test-runner-scripts.ps1`, provados por mutação.

## agentes-locais-instalam-em-pasta-virtualizada

**Regra:** comandos executados de dentro do app desktop do Claude podem ter `%LOCALAPPDATA%`
redirecionado para `AppData\Local\Packages\Claude_*\LocalCache\Local`. Instalação de serviço de
máquina (runner, atalho de inicialização) é feita num PowerShell comum, fora do app.

### Ocorrências

- **Revisão:** observado nos logs da orquestração 44964790517 (pasta de trabalho em `Packages\Claude_*`) — DEV-0008 (#114) e DEV-0135 (#158) · **PR:** #159 (README do runner) · **SHA:** `01130ab`
  - **Impacto:** o runner instalado de dentro do app ficou offline quando a sessão do app reiniciou.
  - **Correção:** runner reiniciado pelo instalador num PowerShell comum (sem `-Reregister`); voltou
    online a partir de `%LOCALAPPDATA%\FitHubGithubRunner` e rodou os bots do PR #159.
  - **Prevenção:** README do runner orienta instalar e reiniciar fora do app.

## testes-de-script-independentes-do-sistema

**Regra:** testes de script que rodam no Windows (local) e no Linux (CI) não podem depender de
ordenação de texto (`Sort-Object`), separador de caminho ou índice fixo do `PATH`: compare conjuntos e
verifique presença.

### Ocorrências

- **Revisão:** CI do PR #159 (job `GitHub Reviewer`, Linux) — DEV-0135 (#158) · **PR:** #159 · **SHA:** `01130ab`
  - **Impacto:** teste falhou no Linux com a função correta; os bots nem rodaram (CI vermelho).
  - **Correção:** comparação do conjunto de arquivos restantes.
  - **Prevenção:** esta regra.
- **Revisão:** CI do PR #140 (índice `[1]` do `PATH` estourou no Linux) — DEV-0125 (#139) · **PR:** #140 · **SHA:** `ff02a9d`
  - **Impacto:** teste do Git for Windows no PATH falhou no Linux.
  - **Correção:** verificação por presença (`-contains`).
  - **Prevenção:** esta regra.

## diff-e-lista-de-arquivos-tem-teto-no-github

**Regra:** o GitHub recusa o diff completo de PRs acima de 300 arquivos (HTTP 406) e corta a lista
`/pulls/N/files` em 3000 arquivos. Quem depende deles monta o diff a partir da lista e confere a lista
contra o total do PR (`changed_files`), falhando fechado se não bater ou se o total faltar no teto.

### Ocorrências

- **Revisão:** orquestração do PR #162 (Agent Code Review: `GitHub API request failed (406)`) — DEV-0137 (#163) · **PR:** #164
  - **Impacto:** PR de 1.448 arquivos (remoção de docs) ficou sem revisão; o mesmo travaria PRs de esqueleto do E0.
  - **Correção:** `readPullRequestDiff` trata o 406 e `synthesizeDiffFromFiles` monta o diff da lista; removidos acima do limite entram só pelo caminho.
  - **Prevenção:** testes de 406, removidos acima do limite e alterados acima do limite, provados por mutação.
- **Revisão:** https://github.com/TitoStauffer/fithub/pull/164#pullrequestreview-6009778773 (HIGH: lista cortada em 3000) — DEV-0137 (#163) · **PR:** #164
  - **Impacto:** acima de 3000 arquivos a revisão seria parcial e o trilho poderia sair leve indevidamente.
  - **Correção:** comparação com `changed_files` e falha fechada quando o total falta e a lista está no teto.
  - **Prevenção:** testes de total divergente e de total ausente no teto, provados por mutação.
- **Validação:** orquestração do PR #162, run 44979145343 (Agent Code Review: `Pull Request diff exceeds the supported review size.`) — DEV-0138 (#167) · **PR:** #168
  - **Impacto:** com 1440 removidos, o cabeçalho de 5 linhas por arquivo no diff sintetizado passou do teto de 350.000 caracteres e o PR ficou sem revisão.
  - **Correção:** removidos viram um bloco compacto no início do diff sintetizado, uma linha por caminho.
  - **Prevenção:** teste de 1440 removidos com caminhos longos abaixo do teto e teste da posição do bloco frente ao `sanitizeAgentDiff`, provados por mutação. Ao mudar o formato de uma entrada do modelo, meça o pior caso contra todos os tetos da cadeia, não só o limite da API.
