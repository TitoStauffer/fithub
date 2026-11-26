#!/bin/sh
# Teste de ponta a ponta do backup e da restauração (DEV-0108), contra o Postgres do compose de desenvolvimento já de pé.
# Roda os scripts num container postgres:17.12 na rede do container do Postgres. Uso, a partir da raiz:
#   sh infra/backup/backup.test.sh
set -eu

compose="docker compose -f infra/compose/docker-compose.dev.yml"
pg=$($compose ps -q postgres)
[ -n "$pg" ] || { echo "O Postgres do compose não está de pé." >&2; exit 1; }
work=$(mktemp -d)
trap 'docker run --rm -v "$work":/backups postgres:17.12 sh -c "rm -rf /backups/*"; rm -rf "$work"' EXIT

run() {
  docker run --rm --network "container:$pg" -v "$work":/backups -v "$PWD/infra/backup":/scripts:ro \
    -e PGHOST=127.0.0.1 -e PGUSER=postgres -e PGPASSWORD=postgres \
    -e FITHUB_DATABASES="account client training nutrition keycloak" postgres:17.12 "$@"
}
sql() {
  run psql -X -At -v ON_ERROR_STOP=1 -d "$1" -c "$2"
}
fail() {
  echo "FALHOU: $1" >&2
  exit 1
}

# Dado de exemplo no banco do client-service, criado pelo dono do banco, como as migrations fariam.
sql client "set role client; drop table if exists deleted_account, sample;
  create table deleted_account (client_id uuid primary key, deleted_at timestamptz not null);
  create table sample (id int primary key);
  insert into sample values (1);
  insert into deleted_account values ('00000000-0000-0000-0000-00000000000a', '2026-11-01T00:00:00Z');"

# Backups antigos para a retenção: um de 40 dias sai, um de 10 dias fica.
run sh -c "mkdir -p /backups/2026-10-01T000000Z /backups/2026-11-10T000000Z &&
  touch -d '40 days ago' /backups/2026-10-01T000000Z && touch -d '10 days ago' /backups/2026-11-10T000000Z"

run sh /scripts/backup.sh
stamp=$(run sh -c "ls /backups | grep -v -e 2026-10-01T000000Z -e 2026-11-10T000000Z")
[ "$(echo "$stamp" | wc -l)" -eq 1 ] || fail "esperava um backup novo, achei: $stamp"
for db in account client training nutrition keycloak COMPLETE; do
  file="$db.dump"; [ "$db" = COMPLETE ] && file=COMPLETE
  run test -f "/backups/$stamp/$file" || fail "o backup não tem $file"
done
run test ! -e /backups/2026-10-01T000000Z || fail "o backup de 40 dias não saiu"
run test -d /backups/2026-11-10T000000Z || fail "o backup de 10 dias saiu antes do prazo"

# Depois do backup: dado novo e uma exclusão nova.
sql client "set role client; insert into sample values (2);
  insert into deleted_account values ('00000000-0000-0000-0000-00000000000b', '2026-11-25T00:00:00Z');"

# Backup incompleto (todos os dumps, mas sem o COMPLETE) é recusado e não muda nada.
run sh -c "mkdir -p /backups/2026-11-20T000000Z && cp /backups/$stamp/*.dump /backups/2026-11-20T000000Z/"
if run sh /scripts/restore.sh 2026-11-20T000000Z 2>/dev/null; then fail "restaurou um backup incompleto"; fi
[ "$(sql client 'select count(*) from sample')" = 2 ] || fail "o backup incompleto mudou o banco"

run sh /scripts/restore.sh "$stamp"

[ "$(sql client 'select count(*) from sample')" = 1 ] && [ "$(sql client 'select max(id) from sample')" = 1 ] \
  || fail "a restauração não voltou o dado ao estado do backup"
[ "$(sql client 'select count(*) from deleted_account')" = 2 ] \
  || fail "a exclusão feita depois do backup se perdeu na restauração"
[ "$(sql client "select pg_get_userbyid(relowner) from pg_class where relname = 'sample'")" = client ] \
  || fail "a tabela restaurada não ficou com o dono do banco"

sql client "set role client; drop table deleted_account, sample;"
echo "Backup e restauração: OK."
