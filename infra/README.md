# infra

Infraestrutura local e de produção.

- `compose/` — `docker-compose.dev.yml`, ambiente local (DEV-0012)
- `keycloak/` — realm versionado (DEV-0024)
- `traefik/` — borda e TLS em produção

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
