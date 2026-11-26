#!/bin/sh
# Restauração de um backup do FitHub (DEV-0108, ADR-012, item 4). Uso: restore.sh <pasta-do-backup>, por exemplo
# restore.sh 2026-11-26T030000Z.
#
# Antes de restaurar, guarda a lista deleted_account atual de cada banco que a tem, numa pasta durável dentro de
# BACKUP_DIR (restore-<backup>-<agora>). Logo depois de restaurar cada banco, regrava a lista dele. Assim, quem excluiu
# a conta depois do backup continua listado, e o serviço reaplica a exclusão (DEV-0172). A pasta só é apagada no fim,
# com tudo restaurado: se algo falhar no meio, ela fica, e a mensagem diz onde (Code Review do PR #314).
#
# A lista tem o mesmo formato nos quatro serviços que a usam (account, client, training e nutrition):
# deleted_account(client_id uuid primary key, deleted_at timestamptz not null). Um backup de antes da migration da lista
# não a tem: a restauração cria a tabela, como dono do banco (DEV-0173). Só o account-service tem backup de antes da
# lista, e a migration dela (V6) usa "if not exists"; nos outros três, a lista veio antes do primeiro backup.
#
# Ambiente: PGHOST, PGPORT, PGUSER e PGPASSWORD de um superusuário; FITHUB_DATABASES; BACKUP_DIR (padrão /backups).
# Cada banco é restaurado com o papel do próprio banco (o dono, como em postgres/init-databases.sh).
set -eu
# A cópia da lista guarda ids de quem excluiu a conta: só o dono lê (arquivos 600, pastas 700).
umask 077

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

saved="$dir/restore-$stamp-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$saved"

# Se o banco tem a lista de contas excluídas. Uma falha ao perguntar aborta a restauração: dentro de um if o set -e não
# vale, então a saída é explícita, para a falha nunca virar "banco sem lista".
has_deleted_list() {
  answer=$(psql -X -At -v ON_ERROR_STOP=1 -d "$1" -c "select to_regclass('public.deleted_account') is not null") || {
    # Sem nada guardado ainda, a cópia vazia não fica; com algo guardado, o rmdir não apaga e a mensagem diz onde está.
    rmdir "$saved" 2>/dev/null || true
    if [ -d "$saved" ]; then
      echo "Não foi possível conferir a lista de contas excluídas de $1: a restauração parou. As listas de antes da" \
        "restauração estão em $saved; regrave-as antes de subir os serviços." >&2
    else
      echo "Não foi possível conferir a lista de contas excluídas de $1: nada foi restaurado." >&2
    fi
    exit 1
  }
  [ "$answer" = "t" ]
}

# Regrava no banco restaurado a lista guardada, sem duplicar quem já estava no backup.
restore_deleted_list() {
  psql -X -q -v ON_ERROR_STOP=1 -d "$1" <<SQL
set role "$1";
create table if not exists deleted_account (client_id uuid primary key, deleted_at timestamptz not null);
create temp table restored_deleted_account (client_id uuid, deleted_at timestamptz);
\\copy restored_deleted_account from '$saved/$1.csv' csv
insert into deleted_account (client_id, deleted_at)
select client_id, deleted_at from restored_deleted_account
on conflict (client_id) do update set deleted_at = greatest(deleted_account.deleted_at, excluded.deleted_at);
-- Marcador para o serviço reaplicar a exclusão das contas da lista (DEV-0172). Um backup de antes da migration do
-- marcador não tem a tabela: ela é criada aqui, pelo dono do banco, e a migration usa "if not exists" (Code Review do
-- PR #316).
create table if not exists restore_pending (restored_at timestamptz not null);
insert into restore_pending (restored_at) values (now());
SQL
}

# 1. A lista de contas excluídas de agora, que o backup pode não ter. Qualquer falha aqui para tudo antes de restaurar.
for db in $FITHUB_DATABASES; do
  if has_deleted_list "$db"; then
    psql -X -q -v ON_ERROR_STOP=1 -d "$db" \
      -c "\\copy (select client_id, deleted_at from deleted_account) to '$saved/$db.csv' csv"
    [ -f "$saved/$db.csv" ] || { echo "A lista de contas excluídas de $db não foi guardada." >&2; exit 1; }
  fi
done

# O dump de cada banco com lista guardada precisa ser legível antes de mexer em qualquer banco (achado do PR #314); nada
# mudou, então a cópia sai. O backup sem a lista é restaurado: a lista guardada volta numa tabela criada pela restauração
# (DEV-0173).
for db in $FITHUB_DATABASES; do
  [ -f "$saved/$db.csv" ] || continue
  if ! pg_restore -l "$source/$db.dump" >/dev/null 2>&1; then
    rm -rf "$saved"
    echo "O dump de $db no backup $stamp está ilegível: nada foi restaurado." >&2
    exit 1
  fi
done

# 2. Cada banco, com o papel do dono, e a lista dele de volta logo em seguida. O schema public é recriado antes: o
#    --clean só remove o que está no dump, e uma tabela de migration posterior ao backup sobraria (o Flyway falharia).
for db in $FITHUB_DATABASES; do
  if ! psql -X -q -v ON_ERROR_STOP=1 -d "$db" \
    -c "drop schema if exists public cascade" -c "create schema public authorization \"$db\"" ||
    ! pg_restore --clean --if-exists --no-owner --no-privileges --role="$db" --dbname="$db" "$source/$db.dump"; then
    echo "A restauração de $db falhou. As listas de contas excluídas de antes da restauração estão em $saved;" \
      "regrave-as antes de subir os serviços." >&2
    exit 1
  fi
  if [ -f "$saved/$db.csv" ] && ! restore_deleted_list "$db"; then
    echo "A lista de contas excluídas de $db não voltou; a lista de antes da restauração está em $saved/$db.csv." >&2
    exit 1
  fi
done

rm -rf "$saved"
echo "Backup $stamp restaurado; listas de contas excluídas preservadas."
