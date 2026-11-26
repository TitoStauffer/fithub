#!/bin/sh
# Backup diário dos bancos do FitHub (DEV-0108, ADR-012, item 4). Grava um pg_dump em formato custom de cada banco de
# FITHUB_DATABASES numa pasta por data e hora (UTC) e só marca a pasta como completa (arquivo COMPLETE) no fim. Depois,
# apaga as pastas de backup e as cópias de restauração (restore-*) com mais de BACKUP_RETENTION_DAYS dias: o dado de quem
# excluiu a conta, e a lista dos ids, não ficam guardados além disso.
#
# Ambiente: PGHOST, PGPORT, PGUSER e PGPASSWORD de um usuário que lê todos os bancos; FITHUB_DATABASES; BACKUP_DIR
# (padrão /backups); BACKUP_RETENTION_DAYS (padrão 30). Nada do conteúdo vai para a saída.
set -eu
# Os dumps têm dado de saúde (NFR-02): só o dono lê (arquivos 600, pastas 700).
umask 077

dir=${BACKUP_DIR:-/backups}
keep=${BACKUP_RETENTION_DAYS:-30}
: "${FITHUB_DATABASES:?Informe os bancos em FITHUB_DATABASES.}"

case "$keep" in
  '' | *[!0-9]*) echo "BACKUP_RETENTION_DAYS precisa ser um número de dias." >&2; exit 1 ;;
esac
if [ "$keep" -lt 1 ]; then
  echo "BACKUP_RETENTION_DAYS precisa ser de ao menos 1 dia." >&2
  exit 1
fi

stamp=$(date -u +%Y-%m-%dT%H%M%SZ)
target="$dir/$stamp"
mkdir -p "$target"

count=0
for db in $FITHUB_DATABASES; do
  pg_dump --format=custom --no-owner --no-privileges --file="$target/$db.dump" "$db"
  count=$((count + 1))
done
# Só agora a pasta vale para restauração: um backup interrompido no meio fica sem este arquivo.
touch "$target/COMPLETE"

# Retenção: saem as pastas com mais de $keep dias, completas ou não, e as cópias de restauração esquecidas. A conta é em
# minutos, com 2 horas de folga, para o backup diário apagar a pasta no dia em que ela completa o prazo, nunca depois.
limit=$((keep * 1440 - 120))
find "$dir" -mindepth 1 -maxdepth 1 -type d \( -name '????-??-??T??????Z' -o -name 'restore-*' \) -mmin +"$limit" \
  -exec rm -rf {} +

echo "Backup $stamp: $count banco(s); retenção de $keep dia(s)."
