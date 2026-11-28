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

# A porta pode ser trocada para simular o banco fora do alcance; a retenção, para testar valores inválidos.
port=5432
retention=30
run() {
  docker run --rm --network "container:$pg" -v "$work":/backups -v "$PWD/infra/backup":/scripts:ro \
    -e PGHOST=127.0.0.1 -e PGPORT="$port" -e PGUSER=postgres -e PGPASSWORD=postgres \
    -e BACKUP_RETENTION_DAYS="$retention" \
    -e FITHUB_DATABASES="account client training nutrition keycloak" postgres:17.12 "$@"
}
sql() {
  run psql -X -At -v ON_ERROR_STOP=1 -d "$1" -c "$2"
}
fail() {
  echo "FALHOU: $1" >&2
  exit 1
}
refused() {
  # O comando precisa falhar e o banco precisa continuar como estava ($1 = linhas de sample esperadas).
  expected=$1
  shift
  if run "$@" >/dev/null 2>&1; then fail "aceitou: $*"; fi
  [ "$(sql client 'select count(*) from sample')" = "$expected" ] || fail "mudou o banco ao recusar: $*"
}

sql client "set role client; drop table if exists deleted_account, sample, later, restore_pending;"

# Um backup de antes de a lista de contas excluídas existir no banco.
run sh /scripts/backup.sh >/dev/null
before_list=$(run sh -c "ls /backups")

# Dado de exemplo no banco do client-service, criado pelo dono do banco, como as migrations fariam.
sql client "set role client;
  create table deleted_account (client_id uuid primary key, deleted_at timestamptz not null);
  create table sample (id int primary key);
  insert into sample values (1);
  insert into deleted_account values ('00000000-0000-0000-0000-00000000000a', '2026-11-01T00:00:00Z');" >/dev/null

# Retenção inválida é recusada.
retention=abc
if run sh /scripts/backup.sh >/dev/null 2>&1; then fail "aceitou retenção não numérica"; fi
retention=0
if run sh /scripts/backup.sh >/dev/null 2>&1; then fail "aceitou retenção de 0 dia"; fi
retention=30

# Pastas antigas para a retenção: as de 40 dias, de 30 dias e de 29 dias e 23 horas saem (o backup diário apaga no dia
# em que a pasta completa o prazo), a de 29 dias fica, e a cópia de restauração esquecida de 40 dias também sai.
run sh -c "mkdir -p /backups/2026-10-01T000000Z /backups/2026-10-11T000000Z /backups/2026-10-11T120000Z \
    /backups/2026-10-12T000000Z /backups/restore-2026-10-01T000000Z-20261001T000000Z &&
  touch -d '40 days ago' /backups/2026-10-01T000000Z /backups/restore-2026-10-01T000000Z-20261001T000000Z &&
  touch -d '30 days ago' /backups/2026-10-11T000000Z && touch -d '719 hours ago' /backups/2026-10-11T120000Z &&
  touch -d '29 days ago' /backups/2026-10-12T000000Z"

run sh /scripts/backup.sh >/dev/null
stamp=$(run sh -c "ls /backups | grep -v -e restore- -e 2026-10-12T000000Z -e $before_list")
[ "$(echo "$stamp" | wc -l)" -eq 1 ] || fail "esperava um backup novo, achei: $stamp"
for file in account.dump client.dump training.dump nutrition.dump keycloak.dump COMPLETE; do
  run test -f "/backups/$stamp/$file" || fail "o backup não tem $file"
done
[ "$(run stat -c %a "/backups/$stamp")" = 700 ] || fail "a pasta do backup não ficou só para o dono (700)"
[ "$(run stat -c %a "/backups/$stamp/client.dump")" = 600 ] || fail "o dump não ficou só para o dono (600)"
run test ! -e /backups/2026-10-01T000000Z || fail "a pasta de 40 dias não saiu"
run test ! -e /backups/2026-10-11T000000Z || fail "a pasta de 30 dias não saiu"
run test ! -e /backups/2026-10-11T120000Z || fail "a pasta de 29 dias e 23 horas não saiu no dia do prazo"
run test -d /backups/2026-10-12T000000Z || fail "a pasta de 29 dias saiu antes do prazo"
run test ! -e /backups/restore-2026-10-01T000000Z-20261001T000000Z || fail "a cópia de restauração de 40 dias não saiu"

# Depois do backup: dado novo, uma exclusão nova e tabelas de migrations posteriores. O backup não tem o marcador de
# restauração, como um backup de antes da migration dele (Code Review do PR #316), nem o hash do e-mail na lista, como
# um backup de antes da V18 do client-service (DEV-0174). A conta "a", que está no backup sem hash, foi excluída de novo
# e ganhou o hash: ele vale sobre o vazio do backup.
sql client "set role client; insert into sample values (2); create table later (id int);
  create table restore_pending (restored_at timestamptz not null);
  alter table deleted_account add column email_hash char(64);
  update deleted_account set email_hash = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' where client_id = '00000000-0000-0000-0000-00000000000a';
  insert into deleted_account values ('00000000-0000-0000-0000-00000000000b', '2026-11-25T00:00:00Z', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb');" >/dev/null

# Recusas, sempre antes de mexer no banco.
run sh -c "mkdir -p /backups/2026-11-20T000000Z && cp /backups/$stamp/*.dump /backups/2026-11-20T000000Z/"
refused 2 sh /scripts/restore.sh 2026-11-20T000000Z
run sh -c "mkdir -p /backups/2026-11-22T000000Z && cp /backups/$stamp/* /backups/2026-11-22T000000Z/ &&
  rm /backups/2026-11-22T000000Z/training.dump"
refused 2 sh /scripts/restore.sh 2026-11-22T000000Z
# Um nome com caminho é recusado, mesmo que aponte para um backup válido.
refused 2 sh /scripts/restore.sh "../backups/$stamp"
port=1
if run sh /scripts/restore.sh "$stamp" >/dev/null 2>&1; then fail "restaurou sem conseguir ler a lista"; fi
port=5432
[ "$(sql client 'select count(*) from sample')" = 2 ] || fail "a falha ao ler a lista mudou o banco"

run sh /scripts/restore.sh "$stamp" >/dev/null

[ "$(sql client 'select count(*) from sample')" = 1 ] && [ "$(sql client 'select max(id) from sample')" = 1 ] \
  || fail "a restauração não voltou o dado ao estado do backup"
[ "$(sql client 'select count(*) from deleted_account')" = 2 ] \
  || fail "a exclusão feita depois do backup se perdeu na restauração"
hash_of_b="select email_hash from deleted_account where client_id = '00000000-0000-0000-0000-00000000000b'"
[ "$(sql client "$hash_of_b")" = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ] \
  || fail "o hash do e-mail da exclusão feita depois do backup se perdeu na restauração"
[ "$(sql client "select email_hash from deleted_account where client_id = '00000000-0000-0000-0000-00000000000a'")"   = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ] || fail "o hash de agora não valeu sobre o vazio do backup"
[ "$(sql client "select pg_get_userbyid(relowner) from pg_class where relname = 'sample'")" = client ] \
  || fail "a tabela restaurada não ficou com o dono do banco"
[ "$(sql client "select to_regclass('public.later') is null")" = t ] \
  || fail "a tabela posterior ao backup sobrou na restauração"
[ "$(sql client 'select count(*) from restore_pending')" = 1 ] \
  || fail "a restauração não gravou o marcador para os serviços reaplicarem as exclusões"
[ "$(sql client "select pg_get_userbyid(relowner) from pg_class where relname = 'restore_pending'")" = client ] \
  || fail "a tabela do marcador não ficou com o dono do banco"
[ -z "$(run sh -c "ls -d /backups/restore-$stamp-* 2>/dev/null || true")" ] \
  || fail "a cópia da restauração que deu certo não saiu"

# Falha no meio da restauração (o dump do training corrompido, depois do client): a lista do client já voltou, e a cópia
# durável fica, só para o dono (Code Review do PR #314).
sql client "set role client;
  insert into deleted_account values ('00000000-0000-0000-0000-00000000000c', '2026-11-26T00:00:00Z');" >/dev/null
run sh -c "mkdir -p /backups/2026-11-21T000000Z && cp /backups/$stamp/* /backups/2026-11-21T000000Z/ &&
  echo corrompido > /backups/2026-11-21T000000Z/training.dump"
if run sh /scripts/restore.sh 2026-11-21T000000Z >/dev/null 2>&1; then fail "a restauração com dump corrompido passou"; fi
[ "$(sql client 'select count(*) from deleted_account')" = 3 ] \
  || fail "a falha no meio da restauração perdeu a lista de contas excluídas"
copy=$(run sh -c "ls -d /backups/restore-2026-11-21T000000Z-*")
run test -f "$copy/client.csv" || fail "a cópia durável da lista não ficou depois da falha"
[ "$(run stat -c %a "$copy")" = 700 ] || fail "a cópia durável não ficou só para o dono (700)"

# Um dump ilegível de um banco com lista é recusado antes de mexer em qualquer banco, sem deixar cópia.
lines=$(sql client 'select count(*) from sample')
run sh -c "mkdir -p /backups/2026-11-23T000000Z && cp /backups/$stamp/* /backups/2026-11-23T000000Z/ &&
  echo corrompido > /backups/2026-11-23T000000Z/client.dump"
if message=$(run sh /scripts/restore.sh 2026-11-23T000000Z 2>&1); then fail "restaurou um dump ilegível"; fi
echo "$message" | grep -q "está ilegível: nada foi restaurado" || fail "a recusa do dump ilegível não diz o motivo"
[ "$(sql client 'select count(*) from sample')" = "$lines" ] || fail "a recusa do dump ilegível mudou o banco"
[ -z "$(run sh -c "ls -d /backups/restore-2026-11-23T000000Z-* 2>/dev/null || true")" ] \
  || fail "a recusa sem mudança deixou uma cópia da lista"

# Um backup de antes de a lista existir (o account-service antes da DEV-0173) é restaurado: a restauração cria a lista,
# como dono do banco, regrava nela a lista de agora e grava o marcador.
run sh /scripts/restore.sh "$before_list" >/dev/null || fail "não restaurou o backup de antes da lista"
[ "$(sql client 'select count(*) from deleted_account')" = 3 ] \
  || fail "a lista de agora não voltou no backup de antes da lista"
[ "$(sql client "$hash_of_b")" = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ] \
  || fail "o hash do e-mail não voltou no backup de antes da lista"
[ "$(sql client "select pg_get_userbyid(relowner) from pg_class where relname = 'deleted_account'")" = client ] \
  || fail "a lista criada pela restauração não ficou com o dono do banco"
[ "$(sql client 'select count(*) from restore_pending')" = 1 ] \
  || fail "a restauração do backup de antes da lista não gravou o marcador"

sql client "set role client; drop table if exists deleted_account, sample, later, restore_pending;" >/dev/null
echo "Backup e restauração: OK."
