# ADR-014 — Borda e deploy em produção

- **Status:** aceita (decisão do Agent na ausência do Tito, para revisão)
- **Data:** 26/11/2026
- **Tarefa:** DEV-0110 (#102)

## Contexto

O ADR-005 decidiu Docker Compose numa VPS, com o Traefik na borda, TLS do Let's Encrypt e deploy por workflow manual
via SSH. Faltava o desenho da borda e do deploy. Três fatos pesam:

- **Caminhos repetidos:** `/clients/...` existe no client, no training e no nutrition, e `/professionals/me` no account
  e no client. Os serviços não podem dividir o mesmo prefixo.
- **CORS:** a web chama as APIs pelo navegador, e nenhum serviço configura CORS (débito #239, DEV-0156).
- **Uma pessoa opera:** quanto menos peças expostas e menos segredos espalhados, melhor.

## Decisão

1. **Um domínio só** (`FITHUB_DOMAIN`), roteado por caminho no Traefik (`infra/traefik/dynamic.yml`):

   | Caminho | Destino |
   |---|---|
   | `/api/account`, `/api/client`, `/api/training`, `/api/nutrition` | o serviço, sem o prefixo |
   | `/auth/realms/fithub/`, `/auth/resources/` | Keycloak (`KC_HTTP_RELATIVE_PATH=/auth`) |
   | `/fithub-photos/`, `/fithub-meal-photos/` | SeaweedFS, com o caminho inteiro (ele entra na assinatura das URLs pré-assinadas) |

   Com tudo na mesma origem, o navegador não precisa de CORS em produção. A web em `/` é da DEV-0176.
2. **Traefik v3 sem o socket do Docker:**
   - a configuração estática vai em argumentos, e as rotas num arquivo;
   - HTTP redireciona para HTTPS, e toda rota manda HSTS (1 ano), `nosniff`, `X-Frame-Options: DENY` e
     `Referrer-Policy: no-referrer`;
   - o certificado vem do Let's Encrypt, pelo desafio HTTP.
3. **Só o Traefik publica portas públicas** (80 e 443). Postgres, RabbitMQ, SeaweedFS e os serviços ficam na rede
   interna. O console do Keycloak (`127.0.0.1:8080`) e o Grafana (`127.0.0.1:3000`) ficam só na VPS, por túnel SSH; a
   borda não expõe o realm `master` nem `/auth/admin`. O console usa o endereço do túnel (`KC_HOSTNAME_ADMIN`), e não o
   domínio público (Code Review do PR #322).
4. **Token:** o `iss` é o endereço público (`https://<domínio>/auth/realms/fithub`), e os serviços buscam as chaves
   do Keycloak pela rede interna (`jwk-set-uri`), sem sair da VPS.
5. **Segredos:** num `.env` na VPS (modelo `infra/compose/.env.prod.example`, só com marcadores). O compose recusa
   subir sem cada um (`${VAR:?}`). Cada usuário de banco tem a sua senha, e cada serviço recebe só a do seu banco:
   comprometer um serviço não abre o banco de outro nem o do Keycloak (Code Review do PR #322). O workflow guarda só o acesso SSH, no environment `production` do GitHub.
6. **Deploy (`.github/workflows/deploy.yml`):**
   - só manual, com o SHA completo de um commit da master que já tem as imagens no GHCR (DEV-0017);
   - copia por SSH só os arquivos da infra desse commit e roda `pull` e `up --wait`;
   - confere a borda no fim (health de cada serviço e o realm);
   - permissão só de leitura; o host é conferido pelo `known_hosts` de um segredo, sem aceitar chave nova;
   - o environment `production` aceita só a branch `master` (regra de deployment branch): o `workflow_dispatch`
     roda o arquivo do workflow da branch escolhida, e sem a regra uma branch com o `deploy.yml` alterado receberia a
     chave SSH.
7. **Operação na VPS:**
   - backup diário às 03:00 UTC, por um container com o `crond`, num volume próprio, com 30 dias de retenção
     (DEV-0108);
   - o `otel-lgtm` com volume e retenção de 30 dias no Loki e no Tempo (ADR-012, ADR-013);
   - os serviços escrevem o log num volume que o coletor lê, com um arquivo por dia e 30 dias;
   - o log de cada container no Docker é limitado a 3 arquivos de 10 MB.

## Alternativas consideradas

- **Um subdomínio por serviço:** cada API numa origem diferente, o que exige CORS em todos os serviços e um
  certificado por nome.
- **CORS na java-commons:** resolve o navegador, mas mantém várias origens e uma lista de origens permitidas para
  manter. Continua sendo o caminho para o desenvolvimento (débito #239).
- **Traefik com o provider do Docker:** rotas por labels, mas o socket do Docker montado no container da borda dá a
  quem o invadir o controle da VPS.
- **Deploy a cada merge:** o ADR-005 decidiu deploy manual, e não há staging.

## Consequências

- **O Tito providencia:**
  - a VPS, com Docker, e o login no GHCR (`docker login ghcr.io` com um token de leitura de pacotes);
  - o domínio apontando para a VPS;
  - o `.env` com os segredos e o SMTP;
  - os segredos do environment `production` (`DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`, `DEPLOY_SSH_KEY`,
    `DEPLOY_SSH_KNOWN_HOSTS`, opcional `DEPLOY_DIR`) e a variável `FITHUB_DOMAIN`;
  - a cópia dos backups para fora da VPS.
- **Até a DEV-0176:** o realm importado tem só os endereços de desenvolvimento (login da web e SMTP do Mailpit), e não
  há imagem da web.
- **Fora da borda:** o RabbitMQ e o Postgres não têm acesso externo; a administração é por túnel SSH.
