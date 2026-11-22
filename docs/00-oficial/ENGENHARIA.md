# Engenharia — FitHub

**Versão:** 1.0.0
**Data:** 2026-11-21
**Status:** Em revisão

> Decisões técnicas, arquitetura, stack, workflow e método de trabalho.
> O "o quê" e o "porquê" do produto estão no [PRD.md](PRD.md); a divisão do
> trabalho está em [BACKLOG.md](BACKLOG.md). Onde este documento conflitar com
> documentos da primeira versão (hoje só no histórico do git, ver
> [docs/HISTORICO.md](../HISTORICO.md)), **vale este**.

Legenda: **DECIDIDO** = escolhido pelo Tito · **PROPOSTO** = sugestão minha,
vale até alguém contestar.

---

## 1. Decisões de stack

| Área | Escolha | Status |
|---|---|---|
| Arquitetura | Microsserviços independentes desde o MVP | DECIDIDO |
| Linguagem / framework | Java 25 + Spring Boot 4 | DECIDIDO |
| Build Java | Maven (parent POM + BOM no monorepo) | PROPOSTO (mantém o atual) |
| Banco | PostgreSQL, um banco lógico por serviço | PROPOSTO |
| Migrations | Flyway | DECIDIDO (mantém) |
| Mensageria | RabbitMQ + outbox transacional | DECIDIDO |
| Identidade | Keycloak (OIDC), login e-mail/senha e Google | DECIDIDO |
| Borda / roteamento | Traefik (TLS, roteamento por path) | PROPOSTO |
| Armazenamento de arquivos | S3 compatível: SeaweedFS local ([ADR-006](adr/ADR-006-seaweedfs-s3-local.md)), Cloudflare R2 (ou similar) em produção | PROPOSTO |
| Web (profissional) | React + Vite + TypeScript, TanStack Query, shadcn/ui | DECIDIDO |
| Mobile (cliente) | React Native + Expo + TypeScript, EAS para build e lojas | DECIDIDO |
| Tipos da API no front | Gerados a partir do OpenAPI de cada serviço | PROPOSTO |
| Deploy inicial | Docker Compose em VPS | DECIDIDO |
| CI/CD | GitHub Actions + imagens no GHCR | PROPOSTO |
| Gestão de tarefas | GitHub Issues + GitHub Projects | DECIDIDO |
| Método | Kanban com marcos por épico | DECIDIDO |
| Git | Trunk-based, PRs curtos, squash merge, chave `DEV-NNNN` em issue, branch, commit e PR | DECIDIDO |
| Revisão | Bots de Code Review e QA no estilo do CashIn (Claude Code CLI em runner self-hosted) + Merge Readiness | DECIDIDO |
| Regras de trabalho | [AGENTS.md](../../AGENTS.md) na raiz | DECIDIDO |

---

## 2. Mapa de serviços

```
            ┌──────────────┐        ┌───────────────┐
            │ Web (React)  │        │ App (Expo)    │
            │ profissional │        │ cliente       │
            └──────┬───────┘        └──────┬────────┘
                   │  HTTPS + JWT          │
                   └──────────┬────────────┘
                        ┌─────▼─────┐        ┌────────────┐
                        │  Traefik  │◄──────►│  Keycloak  │  login, Google,
                        └─────┬─────┘        └────────────┘  tokens, perfis
      ┌──────────────┬────────┼──────────────┬──────────────┐
┌─────▼──────┐ ┌─────▼──────┐ ┌▼────────────┐ ┌▼─────────────┐
│ account    │ │ client     │ │ training    │ │ nutrition    │
│ -service   │ │ -service   │ │ -service    │ │ -service     │
└─────┬──────┘ └─────┬──────┘ └──────┬──────┘ └──────┬───────┘
      │              │               │               │
      └──────────────┴──── RabbitMQ (eventos) ───────┘
      cada serviço com seu próprio banco PostgreSQL
```

### Responsabilidades

| Serviço | É dono de | Lado do produto |
|---|---|---|
| **Keycloak** (pronto, não é código nosso) | Credenciais, login e-mail/senha e Google, recuperação de senha, sessões, papéis (`PERSONAL`, `NUTRITIONIST`, `CLIENT`, `ADMIN`) | Todos |
| **account-service** | Profissional (tipo, CREF/CRN), organização (autônomo = organização de 1 pessoa), assinatura e plano | Personal e Nutri |
| **client-service** | Perfil e perfil de saúde do cliente, vínculos e consentimentos, avaliações corporais (peso, circunferências, dobras, % de gordura), fotos de evolução | Cliente (compartilhado com profissionais autorizados) |
| **training-service** | Base de exercícios e exercícios próprios, restrições de mobilidade, fichas (divisões, versões), execuções por série, recordes | Personal + Cliente |
| **nutrition-service** | Base de alimentos (TACO) e alimentos próprios, restrições e preferências alimentares, planos (refeições, alternativas, versões), registro de refeições | Nutri + Cliente |

### Por que esse recorte
- Os três lados do produto viram serviços (`training` = lado personal,
  `nutrition` = lado nutri). O "lado cliente" se divide: os **dados do
  cliente** ficam no `client-service`, e a **execução** fica junto da
  prescrição (treino no `training`, refeição no `nutrition`), porque a
  execução só faz sentido contra a ficha ou o plano.
- O cliente é dono das avaliações corporais (decisão de produto), então elas
  ficam no `client-service`, e personal e nutri escrevem lá quando autorizados.
- O `client-service` é dono dos vínculos, e é dele que saem os eventos que
  liberam ou cortam o acesso dos profissionais nos outros serviços.

---

## 3. Comunicação entre serviços

**Regra geral (PROPOSTO):** serviços **não chamam uns aos outros de forma
síncrona** no caminho principal. Os fronts chamam cada serviço diretamente
(via Traefik), e os serviços conversam por eventos.

- **Eventos** via RabbitMQ (exchange `topic` por serviço de origem). Publicação
  pelo **outbox transacional**: o evento é gravado na mesma transação do dado e
  um publicador envia ao broker. Consumidores são **idempotentes** (tabela de
  eventos processados).
- **Projeções locais:** cada serviço mantém uma cópia mínima do que precisa
  dos outros. Exemplo: `training` mantém uma tabela de vínculos ativos para
  decidir se o personal pode ver um aluno, sem perguntar ao `client-service` a
  cada request. Isso atende o NFR-06 (um lado cair não derruba o outro).
- **Contratos de eventos** versionados em `libs/event-contracts` (JSON Schema).
  Mudança incompatível = nova versão do evento.

### Eventos iniciais

| Evento | Origem | Consumidores | Para quê |
|---|---|---|---|
| `VinculoAtivado` (cliente, profissional, tipo, escopo) | client | training, nutrition | Liberar acesso do profissional |
| `VinculoEncerrado` | client | training, nutrition | Cortar acesso |
| `ClienteAtualizado` (nome, nascimento, gênero) | client | training, nutrition | Exibir dados básicos sem chamada síncrona |
| `ProfissionalCadastrado` / `AssinaturaAlterada` | account | training, nutrition, client | Bloquear criação de prescrições sem assinatura |
| `ContaExcluida` | client | todos | Exclusão e anonimização (LGPD) |

---

## 4. Segurança e acesso

- Keycloak emite o JWT (OIDC). Cada serviço é um **resource server** que
  valida o token (Spring Security OAuth2 Resource Server). Não existe um
  serviço de identidade próprio. O módulo `identity` atual é **aposentado**.
- **Autorização em duas camadas:** papel (do token) + regra de domínio
  (vínculo ativo com o cliente, consultado na projeção local). Todo endpoint
  que recebe um `clientId` verifica o vínculo. Isso fecha o buraco atual,
  onde qualquer usuário logado lê qualquer paciente.
- A web usa Authorization Code + PKCE. O mobile também, via
  `expo-auth-session`.
- Fotos: bucket privado e URL pré-assinada de curta duração. Nunca é pública.
- Auditoria: acessos de profissionais a dados sensíveis são registrados no
  serviço dono do dado.
- Segredos fora do repositório (`.env` no servidor, GitHub Secrets no CI).

---

## 5. Padrão interno de cada serviço

Mantém o que já existe no código atual:

```
<servico>/src/main/java/com/fithub/<servico>/
├── domain/          agregados, value objects, eventos de domínio, portas (interfaces)
├── application/     casos de uso (um por classe), DTOs de entrada e saída
├── infrastructure/  JPA, RabbitMQ (outbox e consumers), S3, clientes externos
└── interfaces/      controllers REST, tratamento de erros, OpenAPI
```

- Domínio sem dependência de Spring ou JPA (garantido por **ArchUnit**).
- Spring MVC com **virtual threads** (sem WebFlux).
- Erros no padrão **RFC 9457 (Problem Details)**.
- API REST documentada com springdoc-openapi. O OpenAPI gerado alimenta o
  pacote de tipos do front.
- Paginação, datas ISO-8601 em UTC, IDs UUID (v7).

### Bibliotecas compartilhadas
Somente **técnicas**, nunca de domínio:
- `libs/java-commons`: configuração de segurança (resource server),
  Problem Details, base do outbox e de consumidores idempotentes, logging.
- `libs/event-contracts`: schemas dos eventos.
- `libs/java-testing` (só escopo de teste): regras ArchUnit desta seção e a
  base de teste de integração com Postgres e RabbitMQ reais.

Cada serviço escuta numa porta própria no desenvolvimento local: `account`
8081, `client` 8082, `training` 8083, `nutrition` 8084 (o Keycloak usa a 8080).

Domínio compartilhado entre serviços é proibido. Se dois serviços precisam do
mesmo conceito, cada um tem o seu modelo.

---

## 6. Estrutura do monorepo (PROPOSTO)

```
fithub/
├── services/
│   ├── account-service/
│   ├── client-service/
│   ├── training-service/
│   └── nutrition-service/
├── libs/
│   ├── java-commons/
│   ├── java-testing/
│   └── event-contracts/
├── apps/
│   ├── web/                 React + Vite + TS (profissional)
│   └── mobile/              Expo + TS (cliente)
├── packages/
│   └── api-types/           tipos TS gerados do OpenAPI
├── infra/
│   ├── compose/             docker-compose.dev.yml, docker-compose.prod.yml
│   ├── keycloak/            export do realm
│   └── traefik/
├── docs/
│   └── 00-oficial/          PRD, ENGENHARIA, BACKLOG, ADRs
├── pom.xml                  parent Maven (Java 25, Spring Boot 4 BOM)
└── pnpm-workspace.yaml      workspaces JS
```

O código atual (`backend/`, `frontend/`) é **referência**: os agregados e
testes de `training`, `nutrition` e `patients` devem ser portados para os
novos serviços quando cada épico chegar. Ele não é evoluído no lugar.

---

## 7. Ambientes e infraestrutura

| Ambiente | Como roda |
|---|---|
| **Local** | `docker compose -f infra/compose/docker-compose.dev.yml up -d --wait` sobe Postgres (um banco por serviço), RabbitMQ, Keycloak, SeaweedFS (S3) e Mailpit ([infra/README.md](../../infra/README.md)). Os serviços rodam na IDE ou também via compose |
| **Produção** | VPS com Docker Compose: Traefik (TLS Let's Encrypt), Keycloak, RabbitMQ e os serviços. Postgres gerenciado ou em container com volume e backup diário para storage externo |
| **Staging** | Fora do início. Criar quando houver usuários reais (mesma VPS, outro compose) |

- **Backups:** dump diário de cada banco para o storage S3, com retenção e
  teste de restauração mensal.
- **Observabilidade:** logs JSON com `traceId` (Micrometer Tracing /
  OpenTelemetry) desde o início, mais Actuator health. Coleta centralizada
  (Grafana + Loki/Tempo, em container ou plano gratuito de nuvem) a partir do
  épico de operação.

---

## 8. CI/CD

GitHub Actions, com filtro por caminho (só builda o que mudou). O workflow `CI`
tem um job por área (`Java Services`, `Apps`, `Local Infrastructure`, além do
`GitHub Reviewer`). Cada job sempre roda e só trabalha quando a área mudou, e
teste pulado sem `DEV-xxxx` na justificativa derruba o job (DEV-0016; detalhes em
[revisao-automatizada.md](revisao-automatizada.md)).

1. **PR:** `PR title` (título, branch e issue com a mesma chave DEV), build e
   testes do serviço ou app afetado (unitários, ArchUnit, integração com
   Testcontainers), lint e typecheck no front. Precisa estar verde para merge.
   Depois do CI verde rodam Code Review, QA, Coordinator e Merge Readiness
   (AGENTS.md).
2. **Merge na master:** gera a imagem Docker de cada serviço e publica no
   GHCR com a tag do commit (`ghcr.io/<dono>/fithub-<servico>:<sha>`), no job
   `Publish Images`, depois do `Java Services` verde no mesmo commit. Publica
   todos, não só os alterados, para que um run cancelado ou falho não deixe
   serviço sem imagem (cada execução é idempotente). Só esse
   job tem `packages: write`, com o `GITHUB_TOKEN`. Cada serviço tem um
   Dockerfile multi-stage (build Maven e runtime só com o JRE, usuário sem
   privilégio). No PR, o `Java Services` já constrói a imagem dos serviços
   alterados e barra imagem que rode como root (DEV-0017).
3. **Deploy:** workflow manual (`workflow_dispatch`) que entra na VPS por SSH
   e roda `docker compose pull && up -d` para o serviço escolhido. Vira
   automático quando houver staging.
4. **Mobile:** EAS Build e EAS Update disparados manualmente.
5. **Dependências:** Renovate ou Dependabot semanal.

---

## 9. Estratégia de testes

| Camada | Ferramenta | O que cobre |
|---|---|---|
| Domínio | JUnit 5 | Regras de negócio, sem Spring |
| Casos de uso | JUnit 5 + Mockito | Orquestração e portas |
| Arquitetura | ArchUnit | Domínio isolado, sem acesso cruzado entre camadas |
| Persistência e mensageria | Testcontainers (Postgres, RabbitMQ) | Repositórios, migrations, outbox, consumers |
| API | `@SpringBootTest` + MockMvc + token de teste | Contrato HTTP, autorização por vínculo |
| Contratos de evento | Validação contra o JSON Schema | Produtor e consumidor falam a mesma língua |
| Front web | Vitest + React Testing Library | Comportamento das telas |
| Mobile | Jest + React Native Testing Library | Fluxos críticos (registro de série, refeição) |

**Regra:** nada de teste falso (`assertTrue(true)`). Teste desabilitado precisa
de `@Disabled("motivo + DEV-xxxx")`.

---

## 10. Workflow de desenvolvimento

As regras completas (papéis, merge, Code Review, débitos técnicos) estão no
[AGENTS.md](../../AGENTS.md). Resumo:

```
Issue [DEV-0042] - ... em "Pronto" (com critérios de aceitação)
   ↓
Branch a partir da master:  feature|fix|chore/DEV-0042-descricao-curta
   ↓
Commits:  [DEV-0042] - título
          (linha em branco)
          * mudança de impacto
   ↓
PR "[DEV-0042] - título", template preenchido, "Closes #N"
   ↓
CI (PR title + build/testes) → Code Review → QA (trilho completo)
   → Coordinator → Merge Readiness = READY_TO_MERGE
   ↓
Squash merge na master → imagem publicada → deploy só quando o Tito pedir
```

- `master` está sempre compilando e implantável.
- Feature incompleta entra escondida (rota não exposta), nunca quebrada.
- Uma decisão técnica relevante vira **ADR** em `docs/00-oficial/adr/`
  (contexto, decisão, consequências).
- Correção de bug: investigar a causa raiz e apresentar o diagnóstico antes de
  corrigir. Todo bug corrigido ganha um teste de regressão.

### Definition of Ready (tarefa pode entrar em andamento)
- Objetivo e critérios de aceitação claros
- Dependências concluídas
- Cabe em 1 PR, de até 1 a 2 dias

### Definition of Done
- Critérios de aceitação atendidos
- Testes escritos e passando no CI
- Migrations versionadas
- OpenAPI atualizado (se mudou API) e contrato de evento atualizado (se mudou evento)
- Sem segredos no código
- PR revisado e mergeado
- Documentação oficial atualizada, se a tarefa mudou alguma decisão

---

## 11. Método de trabalho

**Kanban com marcos** (dev solo + Claude Code).

### Quadro (GitHub Projects)

| Coluna | Significado |
|---|---|
| Backlog | Tarefas ainda não refinadas |
| Pronto | Atende a Definition of Ready |
| Em andamento | **Limite: 2** |
| Em revisão | PR aberto |
| Feito | Mergeado |

- **Épico = marco** (GitHub Milestone), com uma entrega demonstrável no fim.
- **Tarefa = issue** com título `[DEV-NNNN] - ...` e rótulos: `epico:<id>`,
  `servico:<nome>` ou `app:<web|mobile>`,
  `tipo:<feat|fix|chore|docs|spike|decisao|debito-tecnico>`,
  `tamanho:<P|M>` (P ≤ meio dia, M ≤ 2 dias). Tarefa maior que M deve ser
  quebrada.
- **Débitos técnicos** vindos de revisão ficam no milestone permanente
  `TD — Débitos técnicos` (regra de entrada no AGENTS.md).
- **Revisão semanal** (15 min): o que foi entregue, o que travou, reordenar o
  topo do "Pronto".
- **Fim de épico:** demonstração do fluxo funcionando e revisão rápida (o que
  aprendemos, o que muda no backlog).

### Trabalhando com o Claude Code
- Uma tarefa por sessão. A sessão começa lendo a issue, o PRD e este
  documento.
- Tarefas sem dependência entre si podem rodar em sessões paralelas, cada uma
  na sua branch ou worktree.
- O Claude (papel de PM) abre o PR e faz o merge de qualquer PR com
  `READY_TO_MERGE`; o Tito só é acionado em caso urgente ou decisão de produto
  fora do PRD (AGENTS.md, seção Merge). Deploy só com pedido do Tito.
- Questões em aberto descobertas durante a tarefa são registradas no PRD
  (seção 21) ou como issue, e não resolvidas por conta própria.

---

## 12. Riscos técnicos

| Risco | Mitigação |
|---|---|
| Microsserviços aumentam o custo de cada feature (deploy, contratos, consistência eventual) para um dev solo | Poucos serviços (4), bibliotecas técnicas compartilhadas, compose local com um comando, CI por caminho |
| Consistência eventual na projeção de vínculos (acesso liberado ou cortado com atraso) | Outbox com publicação rápida; corte de acesso tratado como prioridade; testes de integração do fluxo |
| Keycloak é pesado para operar | Realm versionado no repo, imagem oficial, backup do banco dele junto com os outros |
| Spring Boot 4 / Java 25 ainda novos para algumas bibliotecas | Verificar compatibilidade no épico de fundação antes de escolher cada lib |
| VPS única é ponto único de falha | Aceitável no MVP; backups externos e procedimento de restauração documentado |
