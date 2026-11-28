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
- **Revisão:** Code Review do PR #180, no SHA `72669b4` (MEDIUM: publicação de imagens pelo diff de um push) — DEV-0017 (#9) · **PR:** #180
  - **Impacto:** a escolha das imagens pelo diff `before...sha` deixaria sem imagem o serviço de um commit cujo run da master foi cancelado (outro push na fila) ou falhou. O deploy subiria uma imagem antiga, sem aviso.
  - **Correção:** no push na master, o job `Publish Images` publica todos os serviços com a tag do SHA; cada run é idempotente. O diff por serviço fica só no PR, para validar o build.
  - **Prevenção:** ao criar um passo que reage a evento num grupo de `concurrency`, pergunte o que acontece se o run anterior for cancelado ou falhar. A resposta tem que ser "o próximo cobre".

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
- **Revisão:** relatório INCONCLUSIVE do Code Review no PR #287 (SHA `237b51e`) — DEV-0103 (#95) · **PR:** #287
  - **Impacto:** o PR afirmava que o termo de consentimento é idêntico ao `ConsentTerms.CURRENT_TEXT`, mas nenhuma nota citava o arquivo; o critério ficou sem evidência no trilho leve, onde o Code Review é a única conferência.
  - **Correção:** o `termo-de-consentimento.md` cita o `ConsentTerms.java` por link relativo.
  - **Prevenção:** documento do vault que afirma igualdade com código cita esse código por link relativo.

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
- **Validação:** orquestração do PR #162, run 44982067949 (Agent Code Review `INCONCLUSIVE`: cobertura exigia ~1450 arquivos, schema aceita 200) — DEV-0140 (#172) · **PR:** #173
  - **Impacto:** nenhuma resposta do modelo conseguia aprovar um PR com mais de 200 arquivos, mesmo sem bloqueio no conteúdo.
  - **Correção:** acima de 200 arquivos, os removidos saem da matriz de cobertura (`reviewCoverageFiles`); o trilho continua usando a lista completa.
  - **Prevenção:** testes da seleção no limite, do Coordinator com remoção em massa e da classificação do trilho, provados por mutação. Um teto de schema e a regra que o consome precisam ser testados juntos, no pior caso.

## diff-por-caminho-sem-renomeacao

**Regra:** quem decide o que verificar pelos caminhos alterados (filtro de CI, trilho de revisão)
lê o diff com `git diff --name-only --no-renames -z` e separa por `\0`. Com a detecção de renomeação,
padrão do git, um arquivo movido aparece só pelo caminho novo, e a área de onde ele saiu fica sem
verificação. Sem `-z`, o `core.quotePath` põe entre aspas e escapa caminhos com acento, aspas ou
quebra de linha, e o prefixo da área não casa. Teste a regra com um repositório git de verdade: um
`git mv` para fora da área e um arquivo com acento, com `core.quotePath` explícito.

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/179#pullrequestreview-6011325578 (MEDIUM: falso verde no detector de áreas) — DEV-0016 (#8) · **PR:** #179
  - **Impacto:** mover um arquivo de `services/` para fora das áreas deixaria o `Java Services`, check exigido, verde sem compilar o serviço que perdeu a classe. A quebra só apareceria no push da `master`, depois do merge.
  - **Correção:** `changedFiles` em `tools/repo-checks/ci-changes.mjs` usa `--no-renames`; o caminho antigo e o novo entram na classificação.
  - **Prevenção:** teste com repositório git temporário e `git mv services/... legacy/...`, provado por mutação (sem `--no-renames` o teste falha).
- **Revisão:** Code Review do PR #179, rodada 2, no SHA `b801b56` (MEDIUM: caminho com acento entre aspas) — DEV-0016 (#8) · **PR:** #179
  - **Impacto:** uma migration como `V2__criação.sql` em `services/` sairia como `"services/...V2__cria\303\247\303\243o.sql"`, e o `Java Services` ficaria verde sem build.
  - **Correção:** `changedFiles` usa `-z` e separa a saída por `\0`.
  - **Prevenção:** teste com repositório temporário, `core.quotePath=true` e `services/a/V2__criação.sql`, provado por mutação (sem `-z`, só ele falha).

## lockfile-do-gerenciador-em-uso

**Regra:** a ferramenta herdada de outro repositório trata os arquivos gerados do gerenciador de
pacotes que **este** repositório usa. O reviewer do CashIn só resumia `package-lock.json` (npm); no
FitHub, com pnpm, o `pnpm-lock.yaml` entrava cru no diff e no contexto. Ao adotar ou trocar gerenciador
(pnpm, Maven Wrapper, Gradle), confira cada lugar que reconhece arquivo gerado pelo nome. Leitor
feito à mão (sem parser completo) para conteúdo não confiável só é seguro se **falhar fechado em
qualquer linha fora do formato conhecido**: ignorar em silêncio o que não reconhece é como o atacante
esconde a entrada que importa.

### Ocorrências

- **Validação:** orquestração do PR #183, run 45018246655 (Agent Code Review: `Pull Request diff exceeds the supported review size.`) — DEV-0142 (#184) · **PR:** #185
  - **Impacto:** o Expo levou o `pnpm-lock.yaml` a 416 KB. O diff do PR #183 ficou com 443.670 caracteres, 421.720 do lockfile, contra o teto de 350.000, e o PR ficou sem revisão. Todo PR de dependência de app travaria igual.
  - **Correção:** `summarizePnpmLockfile` lê o formato 9 por linha, sem dependência nova, e falha fechado em outra versão. A seção do diff vira marcador com hash, como no npm.
  - **Prevenção:** teste com o lockfile real do repositório, entradas hostis, versão desconhecida e leitura raw acima de 1 MB, provado por mutação.
- **Revisão:** Code Review do PR #185, no SHA `77b7f30` (HIGH: pacote escondido do resumo do pnpm) — DEV-0142 (#184) · **PR:** #185
  - **Impacto:** linha fora do formato na seção `packages:` era ignorada. Isso incluía entrada flow na linha da chave, chave complexa e comentário, e também campo da `resolution` com chave entre aspas. Um tarball de outro host sumia do resumo, que diria "nenhuma anomalia".
  - **Correção:** toda linha da seção é chave nua, propriedade com 4 ou mais espaços ou vazia; o resto lança erro. A `resolution` só aceita campos nus de uma lista fixa (`integrity`, `tarball`, `type`, `repo`, `commit`, `directory`, `path`), sem repetição.
  - **Prevenção:** um teste com cada forma recusada (flow, `?`, comentário, aspas, campo desconhecido, bloco, repetição, recuo, tabulação), provado por mutação de cada guarda.
- **Revisão:** Code Review do PR #185, rodada 2, no SHA `4dbf40d` (HIGH: quebra de linha que só o YAML enxerga; LOW: merge key `<<`) — DEV-0142 (#184) · **PR:** #185
  - **Impacto:**
    - **Quebra escondida:** um CR solto é quebra de linha para o js-yaml do pnpm, mas não para o leitor, que só separava por `\n`/`\r\n`. Dava para pôr um pacote inteiro dentro de uma linha de propriedade ignorada.
    - **Merge key:** a chave `<<` fundia outro mapa na seção e escondia nome e tarball dos pacotes fundidos.
  - **Correção:**
    - CR fora de `\r\n`, NEL, LS, PS e todo controle além de `\t` e `\n` lançam erro antes da leitura.
    - Toda chave de pacote precisa ser `nome@versão`.
  - **Prevenção:** compare o que o leitor considera "linha" com o que o parser de verdade considera. Os testes cobrem CR solto, U+0085, U+2028, U+2029, U+000B, U+0000 e `<<`, e o CRLF continua aceito.
- **Revisão:** Code Review do PR #185, rodada 3, no SHA `61cfd01` (HIGH: comentário na coluna 0 e valor em várias linhas) — DEV-0142 (#184) · **PR:** #185
  - **Impacto:** um comentário na coluna 0 não encerra o mapa no YAML, mas tirava o leitor da seção `packages:`. Os pacotes seguintes sumiam do resumo. Um escalar entre aspas ou uma coleção flow que continuassem na linha seguinte abriam o mesmo caminho.
  - **Correção:** no arquivo inteiro, a coluna 0 só aceita chave de nível superior ou `---`, e toda linha fecha na própria linha as aspas e as coleções flow que abre (`closesOnItsOwnLine`).
  - **Prevenção:** três rodadas acharam três formas de dessincronizar o leitor do parser. A regra geral é a da seção: o leitor feito à mão define uma gramática pequena (linha a linha, sem continuação) e recusa tudo fora dela, em vez de tentar reconhecer o que é perigoso. Os testes cobrem coluna 0 (comentário, `-`, `...`, diretiva, chave entre aspas) e valores abertos (aspas, `{`, `[`, `]` sobrando).
- **Revisão:** Code Review do PR #185, no SHA `af08fbe` (LOW: propriedade do pacote que não é chave nua) — DEV-0143 (#186) · **PR:** #357
  - **Impacto:** com recuo 4, só a linha que começava com `resolution` era conferida. `"resolution"`, `? resolution` e `<<: {resolution: ...}` são a `resolution` para o YAML do pnpm, mas não para o leitor. O pacote aparecia como `(no resolution)`, sem o tarball de verdade.
  - **Correção:** toda propriedade com recuo 4 é chave nua (`^ {4}[A-Za-z][A-Za-z0-9_-]*:( |$)`), e o resto lança erro.
  - **Prevenção:** a gramática pequena vale em cada nível de recuo, não só no das chaves de pacote. Os testes cobrem aspas, `?` e `<<` no recuo 4.
- **Revisão:** Code Review do PR #357, no SHA `c315a04` (LOW: bloco do pacote em recuo maior) — DEV-0143 (#186) · **PR:** #357
  - **Impacto:** para o YAML, a primeira propriedade define o recuo do bloco do pacote. Com o bloco inteiro no recuo 6, a `resolution` valia para o pnpm, e o leitor, que só conferia o recuo 4, a ignorava.
  - **Correção:** a primeira propriedade de cada pacote precisa estar no recuo 4, e o resto lança erro.
  - **Prevenção:** o leitor não presume o recuo que o gerador costuma usar: ele fixa o recuo que aceita e recusa o resto, como faz com a forma das chaves.

## mutante-precisa-de-execucao-de-base

**Regra:** prova por mutação só vale se o comando **passa sem o mutante**. Sem a execução de base, um
comando que já falha (teste com nome errado, arquivo inexistente, teste quebrado) "mata" qualquer
mutante e o manifesto vira evidência falsa. Falha ao executar (executável ausente, timeout, sinal) é
erro, nunca mutante morto.

### Ocorrências

- **Revisão:** Code Review do PR #188, no SHA `222f244` (HIGH: KILLED sem execução de base) — DEV-0116 (#120) · **PR:** #188
  - **Impacto:** `-Dtest=NomeErrado` ou um executável ausente no runner deixariam o job verde com todos os mutantes "mortos". Com o AGENTS aceitando o manifesto como prova, os bots aceitariam essa evidência até para guarda de vínculo.
  - **Correção:** `runManifests` roda cada comando distinto sem mutante e exige status 0 (`BASELINE_FAILED` se não). `defaultRun` lança em erro de execução, e o mutante vira `COMMAND_ERROR`.
  - **Prevenção:** testes de base que falha, comando que não executa e executável ausente, além dos mutantes M8 e M9 do próprio manifesto da DEV-0116.

## detector-liga-a-area-que-le-o-arquivo

**Regra:** o detector de áreas do CI (`tools/repo-checks/ci-changes.mjs`) decide pelo caminho alterado,
mas um teste pode ler arquivo de outra área. Quando um teste da área A lê um arquivo da área B, o
matcher de A inclui o caminho desse arquivo. Senão, mudar só o arquivo deixa o check exigido de A verde
sem conferir, e a quebra só aparece no push da `master`, que roda tudo.

### Ocorrências

- **Revisão:** Code Review do PR #187, no SHA `1b1dff6` (MEDIUM: falso verde entre Java e apps) — DEV-0022 (#14) · **PR:** #187
  - **Impacto:**
    - O teste de esqueleto (Java) compara o OpenAPI com `packages/api-types/openapi/`, mas mudar só esse JSON não ligava o `Java Services`.
    - O teste do `api-types` lê os `services/*/Dockerfile`, mas mudar só um deles não ligava o `Apps`.
  - **Correção:** `OPENAPI_CONTRACT` no matcher `java` e `SERVICE_DOCKERFILE` no matcher `apps`.
  - **Prevenção:** teste de classificação para cada leitura cruzada, provado por mutação. Ao escrever um teste que lê arquivo fora do próprio módulo, ajuste o detector no mesmo PR.

## cache-de-action-nao-serve-em-runner-persistente

**Regra:** job no runner self-hosted não usa o cache de dependências das actions (`cache: npm` do
`setup-node` e parecidos). O runner é persistente: as dependências e o cache do gerenciador já estão no
disco. A action salva o diretório **global** de cache do usuário da máquina, que pode ter gigabytes, e
baixa e descompacta tudo a cada job. Ao mudar um workflow, confira a duração de cada passo numa execução
real, não só se ela passou.

### Ocorrências

- **Validação:** execução 45171542100 da orquestração, em 23/11/2026 — DEV-0154 (#216) · **PR:** o desta correção (DEV-0154)
  - **Impacto:** o `Setup Node.js` levava cerca de 16 minutos em cada etapa com modelo (Code Review e QA),
    restaurando um cache de 1,5 GB, contra 3 a 4 minutos do modelo. Eram cerca de 32 minutos por PR, numa
    fila serial.
  - **Correção:** `package-manager-cache: false` e nenhum `cache` nos dois jobs do runner self-hosted; os jobs
    hospedados continuam com o cache.
  - **Prevenção:** teste de contrato do workflow (`keeps the npm cache off the self-hosted jobs`), provado pelos
    mutantes M1 e M2 de `mutations/DEV-0154.json`.
