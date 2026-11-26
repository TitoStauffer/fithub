# Backup dos bancos

Backup diário e restauração dos bancos do FitHub (DEV-0108, ADR-012, item 4; ADR-005).

## O que fazem

- **`backup.sh`:**
  - grava um `pg_dump` em formato custom de cada banco de `FITHUB_DATABASES`, numa pasta por data e hora (UTC), por
    exemplo `2026-11-26T030000Z/`;
  - só marca a pasta como completa (arquivo `COMPLETE`) quando todos os dumps terminam;
  - apaga as pastas com mais de `BACKUP_RETENTION_DAYS` dias (padrão 30). O dado de quem excluiu a conta não fica
    guardado além desse prazo.
- **`restore.sh <pasta>`:**
  - recusa uma pasta sem o `COMPLETE` ou sem o dump de algum banco, antes de mexer em qualquer coisa;
  - guarda a lista `deleted_account` atual de cada banco que a tem, restaura os bancos com o papel do dono de cada um
    e regrava a lista. Assim, quem excluiu a conta depois do backup continua listado;
  - reapagar o dado dessas contas, que volta com o backup, é a DEV-0172 (#313).

## Ambiente

| Variável | Uso |
|---|---|
| `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` | Superusuário do Postgres (a restauração troca de papel para o dono de cada banco) |
| `FITHUB_DATABASES` | Bancos, separados por espaço: `account client training nutrition keycloak` |
| `BACKUP_DIR` | Pasta dos backups (padrão `/backups`) |
| `BACKUP_RETENTION_DAYS` | Dias de retenção (padrão 30, mínimo 1) |

A saída traz só a data e a quantidade de bancos, nunca o conteúdo.

## Como rodar

Num container `postgres:17.12` com acesso ao Postgres e a pasta dos backups montada em `/backups`. Na VPS, o cron
chama o backup uma vez por dia; o agendamento entra no compose de produção (DEV-0110).

```sh
# backup
docker run --rm --network <rede-do-postgres> -v <pasta-dos-backups>:/backups -v "$PWD/infra/backup":/scripts:ro \
  -e PGHOST=postgres -e PGUSER=postgres -e PGPASSWORD=<senha> \
  -e FITHUB_DATABASES="account client training nutrition keycloak" postgres:17.12 sh /scripts/backup.sh

# restauração: pare os serviços antes, para nada gravar no meio
docker run --rm ... postgres:17.12 sh /scripts/restore.sh 2026-11-26T030000Z
```

## Teste de restauração

`sh infra/backup/backup.test.sh`, com o Postgres do compose de desenvolvimento de pé, roda no CI (job Local
Infrastructure). Ele confere:

- um dump de cada banco e o `COMPLETE`;
- a retenção: a pasta de 40 dias sai, a de 10 dias fica;
- a recusa de uma pasta sem o `COMPLETE`, sem mudar o banco;
- a volta do dado ao estado do backup;
- a preservação da exclusão feita depois do backup;
- o dono das tabelas restauradas.

## Pendências

- **Cópia fora da VPS:** depende do Tito (provedor e custo). Até lá, os backups ficam num volume da própria VPS.
- **Reaplicar as exclusões depois da restauração:** DEV-0172 (#313).
