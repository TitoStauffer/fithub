# Backup dos bancos

Backup diário e restauração dos bancos do FitHub (DEV-0108, ADR-012, item 4; ADR-005).

## O que fazem

- **`backup.sh`:**
  - grava um `pg_dump` em formato custom de cada banco de `FITHUB_DATABASES`, numa pasta por data e hora (UTC), por
    exemplo `2026-11-26T030000Z/`;
  - só marca a pasta como completa (arquivo `COMPLETE`) quando todos os dumps terminam;
  - apaga as pastas com mais de `BACKUP_RETENTION_DAYS` dias (padrão 30), e as cópias de restauração (`restore-*`)
    esquecidas também. A conta é em minutos, com 2 horas de folga, para o backup diário apagar a pasta no dia em que
    ela completa o prazo: o dado de quem excluiu a conta, e a lista dos ids, não ficam guardados além dele.
- **`restore.sh <pasta>`:**
  - recusa uma pasta sem o `COMPLETE` ou sem o dump de algum banco, antes de mexer em qualquer coisa;
  - guarda a lista `deleted_account` atual de cada banco que a tem numa pasta durável, `BACKUP_DIR/restore-<backup>-<agora>/`.
    Uma falha ao ler ou guardar a lista para tudo antes de restaurar;
  - recria o schema `public` de cada banco (o `--clean` só remove o que está no dump, e uma tabela de migration
    posterior ao backup sobraria), restaura com o papel do dono e regrava a lista dele logo em seguida. Assim, quem excluiu a conta depois
    do backup continua listado;
  - apaga a pasta durável só com tudo restaurado. Se algo falhar no meio, ela fica, e a mensagem diz onde: regrave as
    listas antes de subir os serviços;
  - a lista tem o mesmo formato nos três serviços que a usam: `deleted_account(client_id uuid primary key, deleted_at
    timestamptz not null)`;
  - reapagar o dado dessas contas, que volta com o backup, é a DEV-0172 (#313).

## Ambiente

| Variável | Uso |
|---|---|
| `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` | Superusuário do Postgres (a restauração troca de papel para o dono de cada banco) |
| `FITHUB_DATABASES` | Bancos, separados por espaço: `account client training nutrition keycloak` |
| `BACKUP_DIR` | Pasta dos backups (padrão `/backups`) |
| `BACKUP_RETENTION_DAYS` | Dias de retenção (padrão 30, mínimo 1) |

A saída traz só a data e a quantidade de bancos, nunca o conteúdo. Os dois scripts rodam com `umask 077`: os dumps e
as cópias da lista saem só para o dono (arquivos 600, pastas 700), porque têm dado de saúde e ids de contas
excluídas (NFR-02).

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

- um dump de cada banco e o `COMPLETE`, com as permissões 600 e 700;
- a retenção: as pastas de 40 dias, de 30 dias e de 29 dias e 23 horas e a cópia de restauração esquecida saem, a
  de 29 dias fica; retenção inválida é recusada;
- as recusas, sem mudar o banco: pasta sem o `COMPLETE`, pasta sem um dos dumps, nome de pasta inválido e falha ao
  ler a lista;
- a volta do dado ao estado do backup;
- a preservação da exclusão feita depois do backup;
- a falha no meio da restauração (dump corrompido depois do `client`): a lista do `client` já voltou e a cópia
  durável fica;
- o dono das tabelas restauradas e a tabela posterior ao backup removida;
- a recusa de um backup de antes de a lista existir, com a lista de agora guardada.

## Pendências

- **Cópia fora da VPS:** depende do Tito (provedor e custo). Até lá, os backups ficam num volume da própria VPS.
- **Reaplicar as exclusões depois da restauração:** DEV-0172 (#313).
