#!/bin/sh
# Restauração de um backup do FitHub (DEV-0108, ADR-012, item 4). Uso: restore.sh <pasta-do-backup>, por exemplo
# restore.sh 2026-11-26T030000Z.
#
# Antes de restaurar, guarda a lista deleted_account atual de cada banco que a tem; depois, regrava essa lista no banco
# restaurado. Assim, quem excluiu a conta depois do backup continua listado, e o serviço reaplica a exclusão (DEV-0172).
#
# Ambiente: PGHOST, PGPORT, PGUSER e PGPASSWORD de um superusuário; FITHUB_DATABASES; BACKUP_DIR (padrão /backups).
# Cada banco é restaurado com o papel do próprio banco (o dono, como em postgres/init-databases.sh).
set -eu

dir=${BACKUP_DIR:-/backups}
: "${FITHUB_DATABASES:?Informe os bancos em FITHUB_DATABASES.}"
stamp=${1:?Informe a pasta do backup, por exemplo 2026-11-26T030000Z.}

case "$stamp" in
  */* | .*) echo "Pasta de backup inválida: $stamp" >&2; exit 1 ;;
esac
source="$dir/$stamp"
if [ ! -f "$source/COMPLETE" ]; then
  echo "Backup $stamp incompleto ou inexistente: nada foi restaurado." >&2
  exit 1
fi
for db in $FITHUB_DATABASES; do
  if [ ! -f "$source/$db.dump" ]; then
    echo "Backup $stamp sem o banco $db: nada foi restaurado." >&2
    exit 1
  fi
done

saved=$(mktemp -d)
trap 'rm -rf "$saved"' EXIT

has_deleted_list() {
  [ "$(psql -X -At -d "$1" -c "select to_regclass('public.deleted_account') is not null")" = "t" ]
}

# 1. A lista de contas excluídas de agora, que o backup pode não ter.
for db in $FITHUB_DATABASES; do
  if has_deleted_list "$db"; then
    psql -X -q -d "$db" -c "\\copy (select client_id, deleted_at from deleted_account) to '$saved/$db.csv' csv"
  fi
done

# 2. Os bancos, com o papel do dono de cada um.
for db in $FITHUB_DATABASES; do
  pg_restore --clean --if-exists --no-owner --no-privileges --role="$db" --dbname="$db" "$source/$db.dump"
done

# 3. A lista de agora volta, sem duplicar quem já estava no backup.
for db in $FITHUB_DATABASES; do
  if [ -f "$saved/$db.csv" ] && has_deleted_list "$db"; then
    psql -X -q -v ON_ERROR_STOP=1 -d "$db" <<SQL
set role "$db";
create temp table restored_deleted_account (client_id uuid, deleted_at timestamptz);
\\copy restored_deleted_account from '$saved/$db.csv' csv
insert into deleted_account (client_id, deleted_at)
select client_id, deleted_at from restored_deleted_account
on conflict (client_id) do update set deleted_at = greatest(deleted_account.deleted_at, excluded.deleted_at);
SQL
  fi
done

echo "Backup $stamp restaurado; listas de contas excluídas preservadas."
