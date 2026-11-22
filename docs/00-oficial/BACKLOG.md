# Backlog — FitHub MVP

**Versão:** 1.0.0
**Data:** 2026-11-21
**Status:** Em revisão

> Épicos e tarefas derivados do [PRD.md](PRD.md), seguindo a arquitetura e o
> método de [ENGENHARIA.md](ENGENHARIA.md). Cada tarefa vira uma issue no
> GitHub. Cada épico vira um milestone.

Chave: toda tarefa é identificada por `DEV-NNNN` (4 dígitos, sequencial),
que é o prefixo do título da issue no GitHub. Novas issues recebem a chave
automaticamente (DEV-0004).

Tamanho: **P** = até meio dia · **M** = até 2 dias.
Dependência = o que precisa estar pronto antes. Tarefas sem dependência entre
si podem ser feitas em paralelo.

---

## Visão dos épicos

| ID | Épico | Entrega demonstrável | Depende de |
|---|---|---|---|
| W0 | Workflow e automação | PR de baixo risco passa por título, CI, Code Review, QA, Coordinator e Merge Readiness automaticamente | — |
| E0 | Fundação | `docker compose up` sobe tudo; serviço esqueleto com CI verde | DEV-0001 |
| E1 | Identidade e contas | Profissional e cliente fazem login (e-mail e Google) na web e no app | E0 |
| E2 | Cliente e vínculo | Profissional convida, cliente aceita com consentimento e pode encerrar | E1 |
| E3 | Avaliação corporal | Avaliação presencial e online, fotos e comparativo | E2 |
| E4 | Treino: exercícios | Personal busca na base e cadastra exercícios próprios | E1 |
| E5 | Treino: prescrição | Personal publica ficha ABC com restrições e versões | E2, E4 |
| E6 | Treino: execução | Aluno vê o próximo treino, registra por série, recordes, ficha própria | E5 |
| E7 | Nutrição: alimentos | Nutri busca na TACO e cadastra alimentos próprios | E1 |
| E8 | Nutrição: plano | Nutri publica plano com alternativas e versões | E2, E7 |
| E9 | Nutrição: registro | Paciente registra refeições (feita/parcial/não, alternativa, desvio) | E8 |
| E10 | Evolução e painéis | Telas de evolução dos três lados e lista de inativos | E3, E6, E9 |
| E11 | Assinatura | Profissional assina; sem assinatura não prescreve | E1 |
| E12 | LGPD e operação | Exportar e excluir dados, auditoria, backup, observabilidade | E2 |
| E13 | Lançamento | Beta fechado com profissionais reais, app nas lojas | E10, E11, E12 |

### Dependências e trilhas paralelas

```
E0 → E1 ─┬→ E2 ─┬→ E3 ──────────────┐
         │      ├→ E5 → E6 ─────────┤
         ├→ E4 ─┘                   ├→ E10 ─┐
         │      ┌→ E8 → E9 ─────────┘       │
         ├→ E7 ─┘ (E8 também depende de E2) ├→ E13
         ├→ E11 ────────────────────────────┤
         └────── E2 → E12 ──────────────────┘
```

- **Trilha treino** (E4 → E5 → E6) e **trilha nutrição** (E7 → E8 → E9) são
  independentes entre si.
- E3, E11 e E12 podem andar em paralelo às trilhas.

### Sugestão de lançamento em duas ondas (decisão do Tito)

O lado personal sozinho já valida a hipótese principal (profissional paga pela
ferramenta). Uma opção é:

- **Onda 1:** E0–E6 + E10 (parte treino) + E11 + E12 → beta com personais.
- **Onda 2:** E7–E9 + E10 (parte nutrição) → abre para nutricionistas.

A alternativa é lançar tudo junto (E13 depois de todos).

---

## W0 — Workflow e automação

Workflow inspirado no repositório CashIn: chave DEV, template de PR, validação
de título e branch, bots de Code Review e QA, Quality Gate Coordinator e Merge
Readiness. O repositório é privado no plano gratuito, então nada bloqueia o
merge: a regra "merge só com READY_TO_MERGE" fica escrita no `AGENTS.md`.

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0001 | Commitar o estado atual do repositório como base (hoje quase tudo está sem commit) e mover `backend/` e `frontend/` para `legacy/` como referência | P | — |
| DEV-0002 | `AGENTS.md` com as regras do projeto (padronização, git, PR, papéis, Code Review, achados não bloqueantes, Merge Readiness), `CLAUDE.md` apontando para ele e ajuste do ENGENHARIA.md | M | DEV-0001 |
| DEV-0003 | Template de PR adaptado do CashIn e templates de issue (história, bug, tarefa técnica) | P | DEV-0001 |
| DEV-0004 | Bot da chave DEV: Action que dá o próximo número `[DEV-xxxx] - ` a toda issue nova | M | DEV-0001 |
| DEV-0005 | Workflow `PR title`: valida título, branch e issue vinculada com a mesma chave DEV | P | DEV-0001 |
| DEV-0006 | Automação do quadro (status por evento), pós-merge e responsável automático do PR | M | DEV-0001 |
| DEV-0007 | Portar o `tools/github-reviewer` do CashIn: Code Review, QA, Quality Gate Coordinator e Merge Readiness | M | DEV-0002, DEV-0005 |
| DEV-0008 | Runner self-hosted do fithub na máquina do Tito, GitHub App instalado no repositório e secrets | P | DEV-0001 |
| DEV-0009 | Validação ponta a ponta do workflow com um PR de baixo risco | P | DEV-0003, DEV-0005, DEV-0006, DEV-0007, DEV-0008 |

## E0 — Fundação

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0010 | Remover da árvore a documentação da primeira versão (recuperável pelo histórico do Git, ver `docs/HISTORICO.md`) e deixar `docs/00-oficial/` como fonte única | P | DEV-0001 |
| DEV-0011 | Criar a estrutura do monorepo (`services/`, `libs/`, `apps/`, `packages/`, `infra/`), o parent POM (Java 25, Boot 4 BOM) e o `pnpm-workspace` | M | DEV-0001 |
| DEV-0012 | `infra/compose/docker-compose.dev.yml` com Postgres (um banco por serviço), RabbitMQ, Keycloak, S3 local (SeaweedFS, ADR-006) e Mailpit | M | DEV-0011 |
| DEV-0013 | `libs/java-commons`: resource server JWT, Problem Details, logging JSON com traceId | M | DEV-0011 |
| DEV-0014 | `libs/java-commons`: outbox transacional e consumidor idempotente, com testes Testcontainers | M | DEV-0013 |
| DEV-0015 | Template de serviço (esqueleto hexagonal + ArchUnit + Flyway + Actuator + OpenAPI) aplicado aos 4 serviços | M | DEV-0013 |
| DEV-0016 | GitHub Actions: build e testes por caminho para serviços e apps | M | DEV-0015 |
| DEV-0017 | Publicar imagem Docker no GHCR no merge na master | P | DEV-0016 |
| DEV-0018 | `libs/event-contracts` com JSON Schema e validação em teste | P | DEV-0011 |
| DEV-0019 | Templates de issue e PR, rótulos e GitHub Project (quadro Kanban) | P | — |
| DEV-0020 | `apps/web`: React + Vite + TS + TanStack Query + shadcn/ui, com lint e testes | M | DEV-0011 |
| DEV-0021 | `apps/mobile`: Expo + TS, com lint e testes | M | DEV-0011 |
| DEV-0022 | `packages/api-types`: geração de tipos TS a partir do OpenAPI dos serviços | P | DEV-0015, DEV-0020 |
| DEV-0023 | ADR-001 a ADR-005 registrando as decisões de stack de ENGENHARIA.md | P | — |
| DEV-0116 | Verificação por mutação no CI com mutantes declarados (manifesto por tarefa, como no CashIn) | M | DEV-0016 |

## E1 — Identidade e contas

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0024 | Realm `fithub` no Keycloak versionado (clients web e mobile, papéis, política de senha, e-mail via Mailpit) | M | DEV-0012 |
| DEV-0025 | Login com Google no Keycloak | P | DEV-0024 |
| DEV-0026 | Tela de login e cadastro do Keycloak com a identidade visual do FitHub (tema) | M | DEV-0024 |
| DEV-0027 | Web: login PKCE, refresh, logout e rotas protegidas por papel | M | DEV-0020, DEV-0024 |
| DEV-0028 | Mobile: login PKCE com `expo-auth-session`, armazenamento seguro do token | M | DEV-0021, DEV-0024 |
| DEV-0029 | account-service: cadastro do profissional (tipo personal/nutri, CREF/CRN), com organização de 1 pessoa | M | DEV-0015, DEV-0024 |
| DEV-0030 | account-service: evento `ProfissionalCadastrado` | P | DEV-0014, DEV-0029 |
| DEV-0031 | Web: onboarding do profissional (completar dados após o primeiro login) | M | DEV-0027, DEV-0029 |

## E2 — Cliente e vínculo

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0032 | client-service: perfil do cliente (nome, nascimento, gênero, altura, objetivo) | M | DEV-0015, DEV-0024 |
| DEV-0033 | client-service: perfil de saúde (condições gerais + texto livre) | P | DEV-0032 |
| DEV-0034 | client-service: evento `ClienteAtualizado` | P | DEV-0014, DEV-0032 |
| DEV-0035 | client-service: convite por e-mail, link e código do profissional | M | DEV-0032 |
| DEV-0036 | client-service: aceite com consentimento registrado (escopo, data, versão do termo) e regra 1 personal + 1 nutri | M | DEV-0035 |
| DEV-0037 | client-service: encerramento do vínculo (pelo cliente ou pelo profissional) | P | DEV-0036 |
| DEV-0038 | client-service: eventos `VinculoAtivado` e `VinculoEncerrado` | P | DEV-0014, DEV-0036 |
| DEV-0039 | training e nutrition: consumidores que mantêm a projeção de vínculos e clientes, e verificação de acesso por vínculo | M | DEV-0038, DEV-0034 |
| DEV-0040 | client-service: autorização de leitura do perfil e saúde pelo profissional vinculado | P | DEV-0036 |
| DEV-0041 | Mobile: cadastro do perfil e do perfil de saúde | M | DEV-0028, DEV-0033 |
| DEV-0042 | Mobile: aceitar convite (link e código), tela de consentimento, ver e encerrar vínculos | M | DEV-0028, DEV-0037 |
| DEV-0043 | Web: convidar cliente, lista de clientes, ver perfil e saúde, encerrar vínculo | M | DEV-0027, DEV-0040 |

## E3 — Avaliação corporal

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0044 | client-service: avaliação com tipo (presencial/online), peso e circunferências, autor identificado | M | DEV-0040 |
| DEV-0045 | client-service: dobras cutâneas e cálculo de % de gordura por Pollock 3 e 7 dobras | M | DEV-0044 |
| DEV-0046 | client-service: estimativa de % de gordura pela fórmula da Marinha dos EUA | P | DEV-0044 |
| DEV-0047 | client-service: % de gordura de bioimpedância manual | P | DEV-0044 |
| DEV-0048 | client-service: upload de fotos padronizadas (frente/lado/costas) em bucket privado e URL pré-assinada | M | DEV-0044 |
| DEV-0049 | Web: registrar avaliação presencial (com dobras) e ver histórico | M | DEV-0043, DEV-0045 |
| DEV-0050 | Mobile: avaliação online guiada (como medir cada circunferência) e fotos | M | DEV-0041, DEV-0046, DEV-0048 |
| DEV-0051 | Web e mobile: comparativo de fotos lado a lado entre duas datas | M | DEV-0048 |

## E4 — Treino: exercícios

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0052 | **Spike:** validar licença, custo e termos da base de exercícios externa (QUESTÃO-07) e escolher a fonte | P | — |
| DEV-0053 | training-service: modelo de exercício (grupos musculares, equipamento, instruções, mídia por URL) | M | DEV-0015 |
| DEV-0054 | training-service: importação da base externa para cópia local (job reexecutável) | M | DEV-0052, DEV-0053 |
| DEV-0055 | training-service: busca e filtro por nome, grupo muscular e equipamento | P | DEV-0053 |
| DEV-0056 | training-service: exercícios próprios do personal (visíveis só para ele) | P | DEV-0053 |
| DEV-0057 | Web: catálogo de exercícios com busca, vídeo e cadastro de exercício próprio | M | DEV-0027, DEV-0055, DEV-0056 |

## E5 — Treino: prescrição

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0058 | training-service: restrições de mobilidade do aluno (região + descrição) | P | DEV-0039 |
| DEV-0059 | training-service: ficha com N divisões em sequência, validade em semanas, rascunho e publicação | M | DEV-0039, DEV-0053 |
| DEV-0060 | training-service: prescrição por exercício (séries, reps ou faixa, carga, descanso, observação, técnica) e alternativas | M | DEV-0059 |
| DEV-0061 | training-service: versionamento ao editar ficha publicada | M | DEV-0059 |
| DEV-0062 | training-service: alerta ao incluir exercício de região restrita | P | DEV-0058, DEV-0060 |
| DEV-0063 | Web: editor de ficha (divisões, exercícios, prescrição, alternativas, alertas) | M | DEV-0057, DEV-0060, DEV-0062 |
| DEV-0064 | Web: restrições de mobilidade na ficha do aluno e lista de fichas vencendo | P | DEV-0058, DEV-0059 |

## E6 — Treino: execução pelo cliente

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0065 | training-service: próximo treino da sequência e avanço ao finalizar | P | DEV-0059 |
| DEV-0066 | training-service: registro de execução por série (idempotente, vinculado à versão da ficha) | M | DEV-0061 |
| DEV-0067 | training-service: troca por alternativa e comentário do treino | P | DEV-0066 |
| DEV-0068 | training-service: detecção de recordes (maior carga e 1RM estimado) e recorde manual | M | DEV-0066 |
| DEV-0069 | training-service: ficha própria do cliente sem personal | M | DEV-0059 |
| DEV-0070 | Mobile: tela "treino de hoje" com vídeo dos exercícios | M | DEV-0041, DEV-0065 |
| DEV-0071 | Mobile: registro por série com pré-preenchimento (um toque para repetir o prescrito) | M | DEV-0066, DEV-0070 |
| DEV-0072 | Mobile: histórico de treinos e recordes | M | DEV-0068 |
| DEV-0073 | Mobile: montar ficha própria | M | DEV-0069 |

## E7 — Nutrição: alimentos

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0074 | nutrition-service: modelo de alimento (macros por 100 g, etiqueta vegano) | P | DEV-0015 |
| DEV-0075 | nutrition-service: importação da Tabela TACO | M | DEV-0074 |
| DEV-0076 | nutrition-service: medidas caseiras e conversão para gramas/ml | M | DEV-0074 |
| DEV-0077 | nutrition-service: alimentos e receitas próprios da nutri | M | DEV-0074 |
| DEV-0078 | nutrition-service: busca com filtro vegano | P | DEV-0074 |
| DEV-0079 | Web: catálogo de alimentos e cadastro de alimento ou receita própria | M | DEV-0027, DEV-0077, DEV-0078 |

## E8 — Nutrição: plano alimentar

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0080 | nutrition-service: restrições alimentares e preferências do paciente | P | DEV-0039 |
| DEV-0081 | nutrition-service: plano com refeições (nome, horário, ordem), alimentos e quantidades, cálculo de macros da refeição e do dia | M | DEV-0039, DEV-0076 |
| DEV-0082 | nutrition-service: até 2 alternativas por alimento com diferença de macros | M | DEV-0081 |
| DEV-0083 | nutrition-service: versionamento ao editar plano publicado | M | DEV-0081 |
| DEV-0084 | nutrition-service: alerta de alimento conflitante com alergia ou intolerância | P | DEV-0080, DEV-0081 |
| DEV-0085 | Web: editor de plano (refeições, alimentos, alternativas, macros, alertas) | M | DEV-0079, DEV-0082, DEV-0084 |
| DEV-0086 | Web: restrições e preferências na ficha do paciente | P | DEV-0080 |

## E9 — Nutrição: registro pelo paciente

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0087 | nutrition-service: registro de refeição (feita/parcial/não feita, alternativa usada), idempotente e vinculado à versão | M | DEV-0083 |
| DEV-0088 | nutrition-service: desvio em texto livre e foto opcional do prato | P | DEV-0087 |
| DEV-0089 | Mobile: "refeições de hoje" com macros e alternativas | M | DEV-0041, DEV-0082 |
| DEV-0090 | Mobile: registrar refeição, alternativa usada e desvio | M | DEV-0087, DEV-0089 |

## E10 — Evolução e painéis

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0091 | training-service: consultas de aderência, evolução de carga e volume | M | DEV-0066 |
| DEV-0092 | nutrition-service: consultas de aderência, alternativas mais usadas e desvios | M | DEV-0087 |
| DEV-0093 | client-service: série histórica de peso, medidas e % de gordura por método | P | DEV-0044 |
| DEV-0094 | Web personal: painel do aluno (aderência, cargas, recordes, evolução corporal) | M | DEV-0091, DEV-0093 |
| DEV-0095 | Web nutri: painel do paciente (aderência, alternativas, desvios, evolução corporal) | M | DEV-0092, DEV-0093 |
| DEV-0096 | Mobile: tela de evolução do cliente (treino, dieta, corpo) | M | DEV-0091, DEV-0092, DEV-0093 |
| DEV-0097 | Web: lista de clientes com sinalização de inativos há X dias | P | DEV-0091, DEV-0092 |

## E11 — Assinatura

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0098 | **Decisão:** planos, preço, teste grátis e meio de pagamento (QUESTÃO-01) | P | — |
| DEV-0099 | account-service: plano e estado da assinatura (teste, ativa, vencida) | M | DEV-0029 |
| DEV-0100 | account-service: integração com o meio de pagamento escolhido (checkout e webhook idempotente) | M | DEV-0098, DEV-0099 |
| DEV-0101 | account-service: evento `AssinaturaAlterada` e bloqueio de criação de prescrições em training e nutrition | M | DEV-0099, DEV-0039 |
| DEV-0102 | Web: tela de plano e assinatura | M | DEV-0100 |

## E12 — LGPD e operação

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0103 | Termos de uso e política de privacidade (texto) e versão do termo no consentimento | P | — |
| DEV-0104 | Exportação dos dados do cliente (todos os serviços, via evento e agregação) | M | DEV-0038 |
| DEV-0105 | Exclusão de conta com evento `ContaExcluida` e anonimização em cada serviço | M | DEV-0038, DEV-0107 |
| DEV-0106 | Auditoria de acesso de profissionais a dados sensíveis | M | DEV-0040 |
| DEV-0107 | **Decisão:** política de retenção (QUESTÃO-06) | P | — |
| DEV-0108 | Backup diário dos bancos e teste de restauração documentado | M | DEV-0012 |
| DEV-0109 | Observabilidade centralizada (logs e traces) | M | DEV-0013 |
| DEV-0110 | `docker-compose.prod.yml`, Traefik com TLS e workflow de deploy por SSH | M | DEV-0017 |

## E13 — Lançamento

| ID | Tarefa | Tam. | Depende |
|---|---|---|---|
| DEV-0111 | Recrutar 3 a 5 profissionais para o beta | P | — |
| DEV-0112 | Contas de desenvolvedor Apple e Google e publicação em teste fechado (TestFlight e teste interno) | M | DEV-0021 |
| DEV-0113 | Instrumentação das métricas de sucesso do PRD | M | E10 |
| DEV-0114 | Beta fechado e coleta de feedback | M | DEV-0111, DEV-0112, DEV-0110 |
| DEV-0115 | Publicação nas lojas | M | DEV-0114 |

---

## Primeiras tarefas sugeridas

1. **DEV-0001** commitar a base e mover o legado (bloqueia todo o resto)
2. **W0 (DEV-0002 a DEV-0009)** workflow e bots, antes de começar a E0

Em paralelo, sem código:

- **DEV-0052** spike da base de exercícios
- **DEV-0098** decisão de assinatura e preço (decisão de negócio)
- **DEV-0111** conversar com profissionais (validação da PREMISSA-01)
