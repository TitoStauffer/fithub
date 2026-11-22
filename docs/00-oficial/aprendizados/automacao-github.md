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

- **Revisão:** aviso do git antes do commit — DEV-0130 (#147) · **PR:** #148 · **SHA:** `d77d381` (branch; o PR ainda não foi integrado)
  - **Impacto:** risco de o hook `pre-push` falhar silenciosamente no Windows. Pego no aviso do git
    antes do commit.
  - **Correção:** regras `eol=lf` no `.gitattributes`.
  - **Prevenção:** `git ls-files --eol .githooks` mostra `i/lf w/lf`.

## agentes-locais-instalam-em-pasta-virtualizada

**Regra:** comandos executados de dentro do app desktop do Claude podem ter `%LOCALAPPDATA%`
redirecionado para `AppData\Local\Packages\Claude_*\LocalCache\Local`. Instalação de serviço de
máquina (runner, atalho de inicialização) deve ser feita num PowerShell comum, fora do app.

### Ocorrências

- **Revisão:** observado nos logs da orquestração 44964790517 — DEV-0008 (#114) · **SHA:** `ff02a9d` (HEAD da master na execução)
  - **Impacto:** o runner do fithub ficou na pasta virtualizada; o atalho de login aponta para o
    caminho real e pode não iniciar o runner após reiniciar o Windows.
  - **Correção:** pendente — reinstalar o runner num PowerShell fora do app (ação do Tito).
  - **Prevenção:** README do runner orienta instalar fora do app.
