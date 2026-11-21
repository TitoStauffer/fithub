# Runner self-hosted local (Code Review e QA dos bots)

Portado do CashIn (DEV-0008). Os jobs de Code Review e QA dos bots (DEV-0118)
rodam no runner self-hosted **`fithub-agents-windows`** (labels
`self-hosted, Windows, X64, fithub-agents`), porque o Claude Code CLI usa a
assinatura já autenticada nesta máquina. Sem esse runner online, a orquestração
fica na fila e o `Merge Readiness` permanece `INCONCLUSIVE`.

Os scripts não exigem administrador. Tudo fica em `%LOCALAPPDATA%\FitHubGithubRunner`:

| Pasta | Conteúdo |
|---|---|
| `runner/` | `actions/runner` oficial, registro e `_work` dos jobs |
| `pwsh/` | PowerShell 7 portátil, exigido pelos jobs (`shell: pwsh`) |
| `scripts/` | Cópias instaladas de `runner-common.ps1`, `start-runner.ps1` e `ensure-runner.ps1`, executadas pelo atalho de inicialização e pelo hook |
| `logs/runner.log` | Saída do runner; acima de 5 MB vira `runner.log.1` no próximo início |

## Convivência com o runner do CashIn

Numa conta pessoal o GitHub não compartilha runner entre repositórios, então a
máquina roda dois listeners: o do CashIn (`%LOCALAPPDATA%\CashInGithubRunner`)
e este. O runner do fithub reconhece o listener do CashIn como **instalação
irmã** (`$SiblingListenerPaths` em `runner-common.ps1`) e inicia mesmo com ele
rodando.

**Limitação no lado do CashIn:** os scripts do CashIn tratam qualquer outro
`Runner.Listener` como "outra instalação" e recusam iniciar
(`foreign-listener`). Se o runner do fithub subir primeiro (atalho de login),
o do CashIn não sobe pelo hook até o CashIn reconhecer o fithub como irmão.

## Pré-requisitos

- Windows x64 com o Claude Code CLI instalado e logado (`claude` no `PATH`).
- GitHub CLI autenticado (`gh auth status`) com conta administradora do repositório.

## Instalar ou reparar

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/github-runner/install-runner.ps1
```

Rode a partir de um checkout atualizado da `master`: os scripts copiados para
`scripts/` são os que o hook e o atalho executam.

O instalador baixa o runner e o PowerShell das releases oficiais e confere o
SHA-256 fixado em `runner-common.ps1`, registra o runner com `--replace`, copia
os scripts para `scripts/`, cria o atalho `FitHub GitHub Runner.lnk` na pasta
Inicializar do Windows e inicia o runner. `-Reregister` força novo registro;
`-NoStart` não inicia ao final.

- **Token de registro:** obtido pelo `gh` na hora e entregue ao `config.cmd`
  pela variável `ACTIONS_RUNNER_INPUT_TOKEN`, removida logo depois. Nunca
  aparece na linha de comando nem é gravado em arquivo.
- **Reparação:** o SHA-256 do pacote extraído fica em `.fithub-archive-sha256`;
  pasta sem marcador ou com versão diferente é reextraída (com o runner
  parado), preservando registro e credenciais.

## Início automático

- **Login no Windows:** o atalho executa `scripts/start-runner.ps1`.
- **Sessões do Claude Code neste repositório:** o hook `SessionStart` em
  `.claude/settings.json` executa só a cópia **instalada** de
  `ensure-runner.ps1`, nunca a da árvore de trabalho. Ele inicia o runner se
  estiver parado, nunca baixa nem registra nada e sempre termina com sucesso.

## Verificação automatizada

`tools/github-runner/test-runner-scripts.ps1` roda no job `GitHub Reviewer` do
CI e localmente, sem baixar, registrar ou iniciar nada. Valida sintaxe e
codificação, o hook, que o token não vai na linha de comando, os helpers
(detecção de listener, instalação irmã, rotação de log, marcador de reparação)
e a normalização do token.

## Atualizar versões

1. `gh api repos/actions/runner/releases/latest` e
   `gh api repos/PowerShell/PowerShell/releases/latest` (campo `digest` do asset `win-x64`).
2. Atualize versão e SHA-256 em `runner-common.ps1`.
3. Com o runner parado, rode o instalador de novo.

## Remover

Com o runner parado: `runner\config.cmd remove --token <token>` (token de
`gh api -X POST repos/TitoStauffer/fithub/actions/runners/remove-token --jq .token`),
depois apague `%LOCALAPPDATA%\FitHubGithubRunner` e o atalho
`FitHub GitHub Runner.lnk` da pasta Inicializar.
