# infra

Infraestrutura local e de produção.

- `compose/` — `docker-compose.dev.yml`, ambiente local (DEV-0012); `docker-compose.prod.yml`, produção (DEV-0110)
- `backup/` — backup diário dos bancos e restauração, com o teste de ponta a ponta (DEV-0108)
- `keycloak/` — realm `fithub` versionado (DEV-0024), com o teste das guardas e a checagem no Keycloak de pé
- `observability/` — configuração do coletor OpenTelemetry do `otel-lgtm`, com o teste de ponta a ponta (DEV-0109)
- `traefik/` — rotas da borda em produção (DEV-0110)

## Ambiente local

Na raiz do repositório:

```sh
docker compose -f infra/compose/docker-compose.dev.yml up -d --wait   # sobe e espera todos saudáveis
docker compose -f infra/compose/docker-compose.dev.yml ps             # estado e healthcheck
docker compose -f infra/compose/docker-compose.dev.yml down           # para, mantendo os dados
```

Todas as portas ficam só em `127.0.0.1`. Credenciais abaixo são **apenas de desenvolvimento**.
Para trocar porta ou senha, copie `compose/.env.example` para `compose/.env` (ignorado pelo Git).

| Serviço | Porta | Acesso |
|---|---|---|
| PostgreSQL 17 | 5432 | superusuário `postgres` / `postgres` |
| Bancos dos serviços | 5432 | bancos `account`, `client`, `training`, `nutrition`: usuário igual ao nome do banco, senha `fithub` |
| RabbitMQ | 5672 (AMQP), 15672 (painel) | `fithub` / `fithub` — painel em http://localhost:15672 |
| Keycloak | 8080 | admin `admin` / `admin` — http://localhost:8080 (banco `keycloak` no Postgres) |
| SeaweedFS (S3) | 8333 | access key `fithub-dev`, secret `fithub-dev-secret`, endpoint http://localhost:8333 ([ADR-006](../docs/00-oficial/adr/ADR-006-seaweedfs-s3-local.md)) |
| Mailpit | 1025 (SMTP), 8025 (painel) | sem autenticação — e-mails em http://localhost:8025 |

Cada serviço só conecta no próprio banco: o `CONNECT` público é revogado na criação.

### Observabilidade (logs e traces)

O `otel-lgtm` (coletor OpenTelemetry, Loki, Tempo, Prometheus e Grafana num container só,
[ADR-013](../docs/00-oficial/adr/ADR-013-observabilidade.md)) fica no perfil `observability`, porque a
imagem tem 2,8 GB. Na raiz:

```sh
mkdir -p .logs   # antes de subir: o coletor lê os logs dos serviços daqui
docker compose -f infra/compose/docker-compose.dev.yml --profile observability up -d --wait
```

| Serviço | Porta | Acesso |
|---|---|---|
| Grafana | 3000 (`GRAFANA_PORT`) | sem login, só local — http://localhost:3000 |
| Coletor OTLP/HTTP | 4318 (`OTLP_HTTP_PORT`) | recebe os spans dos serviços |

Para o serviço mandar traces e logs, rode-o com estas variáveis (exemplo do client-service):

```sh
MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://localhost:4318/v1/traces
LOGGING_FILE_NAME=<raiz do repositório>/.logs/client-service.log
```

- Sem o endpoint, o serviço não exporta nada; sem o arquivo, só escreve no console.
- No Grafana, **Explore → Tempo** mostra o trace de uma requisição, inclusive o consumidor do evento no RabbitMQ.
  **Explore → Loki** mostra os logs: `{service_name="client-service"} | trace_id="<traceId>"`.
- **Métricas de sucesso (DEV-0113):** o painel **FitHub — Métricas de sucesso** vem provisionado (`observability/grafana/`).
  Para ver números do serviço local, rode-o com `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true` e
  `MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://localhost:4318/v1/metrics`. As definições estão no PRD, seção 19.
- O container não tem volume: os dados somem quando ele é removido (`down`).
- **Teste de ponta a ponta:** `sh infra/observability/observability.test.sh`, com o perfil de pé. Ele manda um span
  e uma linha de log com o mesmo `traceId` e confere que o Tempo e o Loki mostram os dois, e que uma métrica chega ao Prometheus e o painel está provisionado. Roda no job Local
  Infrastructure.

### Realm do Keycloak

O Keycloak importa `keycloak/realm-fithub.json` na subida, **só quando o realm `fithub` ainda não
existe** no banco. O arquivo define os clients `fithub-web` e `fithub-mobile` (Authorization Code +
PKCE), os papéis `client`, `personal`, `nutritionist` e `admin`, o cadastro por e-mail e o envio de
e-mail pelo Mailpit ([ADR-007](../docs/00-oficial/adr/ADR-007-papeis-e-clients-do-realm.md)).

- **Criar uma conta de teste:** abra o cadastro pelo login de um client ou crie o usuário no console
  (http://localhost:8080, realm `fithub`). O e-mail de verificação chega em http://localhost:8025.
  Nenhum usuário é versionado.
- **Alterar o realm:** edite o JSON, rode `node --test infra/keycloak/realm.test.mjs` e recrie o realm
  local. O jeito mais simples é resetar os dados (abaixo); para manter os outros bancos, apague só o
  realm `fithub` no console e reinicie o Keycloak.
- **Tema das telas e dos e-mails:** `keycloak/themes/fithub`, montado no Keycloak do compose. Ele
  herda as telas do `keycloak.v2`: mude só o CSS (`login/resources/css/fithub.css`), os textos
  (`login/messages/messages_pt_BR.properties`) e a moldura dos e-mails (`email/html/template.ftl`).
  No `start-dev` o Keycloak não guarda o tema em cache: basta recarregar a página. Não adicione
  script, página `.ftl` de login nem recurso de outro domínio; o teste das guardas recusa.
- **Cadastro:** a tela pede e-mail e nome. Depois de confirmar o e-mail (a mensagem chega no
  Mailpit), a pessoa define a senha.
- **Client do account-service:** `fithub-account-service`, confidencial, só para atribuir o papel
  de profissional (ADR-008). O secret vem de `FITHUB_ACCOUNT_SERVICE_SECRET` (o import do realm troca
  o marcador `${FITHUB_ACCOUNT_SERVICE_SECRET}` pelo valor); no compose local o padrão é
  `fithub-dev-account-service`, só de desenvolvimento.
- **Conferir o realm de pé:** `node infra/keycloak/check-live-realm.mjs http://localhost:8080`.
- Mudança feita só pelo console não vai para o repositório: leve para o JSON o que precisar ficar.
- Nunca coloque usuário, senha ou secret no arquivo; o teste das guardas recusa.

### Porta ocupada

Se a máquina já usa uma das portas (um Postgres local na 5432, por exemplo), sobrescreva só para o
comando ou no `.env`:

```sh
POSTGRES_PORT=55432 docker compose -f infra/compose/docker-compose.dev.yml up -d --wait
```

### Resetar os dados

Os bancos e usuários são criados só na primeira subida, com o volume vazio
(`compose/postgres/init-databases.sh`). Para recomeçar do zero, apague os volumes:

```sh
docker compose -f infra/compose/docker-compose.dev.yml down -v
```

## Produção (DEV-0110)

Docker Compose numa VPS, com o Traefik na borda ([ADR-005](../docs/00-oficial/adr/ADR-005-deploy-compose-vps.md),
[ADR-014](../docs/00-oficial/adr/ADR-014-borda-e-deploy.md)). **Nada foi implantado ainda:** o deploy depende das
peças abaixo, que são do Tito.

### Borda

Um domínio só (`FITHUB_DOMAIN`), com TLS do Let's Encrypt e HTTP redirecionado para HTTPS:

| Caminho | Destino |
|---|---|
| `/api/account`, `/api/client`, `/api/training`, `/api/nutrition` | o serviço, sem o prefixo (`/api/client/clients/me` chega como `/clients/me`) |
| `/auth/realms/fithub/`, `/auth/resources/` | Keycloak |
| `/fithub-photos/`, `/fithub-meal-photos/` | SeaweedFS (URLs pré-assinadas das fotos) |

Só o Traefik publica portas (80 e 443). O console do Keycloak e o Grafana ficam em `127.0.0.1` na VPS:

```sh
ssh -L 8080:127.0.0.1:8080 -L 3000:127.0.0.1:3000 <usuario>@<vps>   # console em http://localhost:8080/auth/admin, Grafana em http://localhost:3000
```

O console usa o endereço do túnel (`KC_HOSTNAME_ADMIN`): abra pela porta 8080 local, ou ajuste `KEYCLOAK_ADMIN_PORT`.

### Preparar a VPS (uma vez)

1. Instale o Docker e faça login no GHCR com um token só de leitura de pacotes: `docker login ghcr.io`.
2. Aponte o DNS do domínio para a VPS e libere só as portas 22, 80 e 443 no firewall.
3. Crie a pasta do deploy (padrão `~/fithub`) e, nela, `infra/compose/.env` a partir de
   `compose/.env.prod.example`, com cada `<...>` trocado e `chmod 600`. Cada usuário de banco tem a sua senha
   (`ACCOUNT_DB_PASSWORD`, `CLIENT_DB_PASSWORD`…, `KEYCLOAK_DB_PASSWORD`), e cada serviço recebe só a do seu banco.
   No RabbitMQ, o administrador (`RABBITMQ_ADMIN_*`) fica só para a operação: cada serviço tem o seu usuário
   (`<SERVIÇO>_RABBITMQ_PASSWORD`), que só publica na própria exchange e só configura as próprias filas e a própria
   exchange. As quatro exchanges são declaradas pelo `rabbitmq-users`, e os serviços sobem com
   `FITHUB_MESSAGING_DECLARE_SOURCE_EXCHANGES=false` (DEV-0177). No S3, o client e o nutrition têm cada um
   a sua identidade (`CLIENT_S3_*`, `NUTRITION_S3_*`), só com o próprio bucket.
4. **Asaas (DEV-0100, [ADR-015](../docs/00-oficial/adr/ADR-015-pagamento-asaas.md)):**
   - crie a conta de produção (pede CNPJ, MEI serve, e conta bancária) e gere a chave da API;
   - em **Integrações > Webhooks**, cadastre `https://<domínio>/api/account/webhooks/asaas`, com a fila sequencial
     e um token de 32 a 255 caracteres;
   - ponha a chave e o token no `.env` (`ASAAS_API_KEY`, `ASAAS_WEBHOOK_TOKEN`).

   Para testar antes, use o sandbox, com `ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3` e a chave do sandbox.
5. No GitHub, crie o environment `production` com os segredos `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`, `DEPLOY_SSH_KEY`
   (chave só para o deploy), `DEPLOY_SSH_KNOWN_HOSTS` (saída de `ssh-keyscan <vps>`, conferida) e, se a pasta não for
   a padrão, `DEPLOY_DIR`; e a variável `FITHUB_DOMAIN`. Em **Deployment branches**, deixe só a `master`: o
   `workflow_dispatch` roda o arquivo do workflow da branch escolhida, e sem essa regra uma branch com o `deploy.yml`
   alterado receberia a chave SSH. Se quiser uma segunda trava, ative **Required reviewers** com você mesmo.

### Implantar

Actions → **Deploy** → *Run workflow*, com o SHA completo de um commit da master cujo `Publish Images` passou. O
workflow:
1. confere que o commit está na master;
2. copia a infra desse commit para a VPS;
3. roda `pull` e `up -d --wait` com as imagens desse SHA;
4. confere o health de cada serviço e o realm pela borda.

Voltar uma versão é rodar de novo com o SHA anterior.

### Operação

- **Backup:** o container `backup` roda a `backup/backup.sh` todo dia às 03:00 UTC (`BACKUP_SCHEDULE`), no volume
  `backups`, com 30 dias de retenção. A cópia para fora da VPS ainda é do Tito. Para restaurar, veja
  [backup/README.md](backup/README.md).
- **Logs e traces:** o `otel-lgtm` guarda 30 dias no Loki e no Tempo (`observability/loki-config.yaml` e
  `tempo-config.yaml`); cada serviço escreve um arquivo de log por dia no volume `logs`, com 30 dias.
- **Web (DEV-0176):** a imagem `fithub-web` sai junto com as dos serviços, na mesma tag, e fica em `/`, com a
  menor prioridade no Traefik. Ela chama as APIs e o Keycloak por caminho (`/api/<serviço>`, `/auth`), no mesmo
  domínio, e devolve 404 para `/api`, `/auth` e os buckets que chegam a ela, como o console de administração.
- **Realm de produção (DEV-0176):** o realm importado usa `FITHUB_WEB_URL` (redirect URI, origem e logout da web) e
  `FITHUB_SMTP_*` (e-mail de verificação do cadastro), que o compose de produção passa ao Keycloak. Sem eles, o realm
  fica com os valores do desenvolvimento: `http://localhost:5173` e o Mailpit. O remetente desse e-mail é
  `KEYCLOAK_MAIL_FROM`, só o endereço. O import só roda com o realm ainda inexistente no banco: num banco que já tem o
  realm, mude esses campos pelo console.

### Testes

- `node --test infra/compose/prod.test.mjs`: guardas das portas, dos segredos obrigatórios, das imagens fixadas, das
  rotas, da retenção e do workflow de deploy (lê o compose resolvido pelo Docker Compose).
- `sh infra/compose/prod-smoke.test.sh`: sobe a borda, o Keycloak em modo de produção, o SeaweedFS, o backup e o
  `otel-lgtm` com valores de teste, e confere as rotas, o HSTS, o realm `master` fora da borda e o backup agendado.
  Usa as portas 8880 e 8443 (`FITHUB_HTTP_PORT`, `FITHUB_HTTPS_PORT`).

Os dois rodam no job Local Infrastructure.
