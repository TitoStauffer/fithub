#!/bin/sh
# Agenda o backup diário no compose de produção (DEV-0110): o crond do BusyBox roda a backup.sh no horário de
# BACKUP_SCHEDULE (padrão 03:00 UTC). O crond não passa o ambiente do container para o job, então as variáveis do
# backup vão para um arquivo só do root, lido pelo job. A saída do job vai para o log do container.
set -eu
umask 077
: "${BACKUP_SCHEDULE:=0 3 * * *}"
export -p | grep -E '^export (PG[A-Z]*|FITHUB_DATABASES|BACKUP_DIR|BACKUP_RETENTION_DAYS)=' >/root/backup.env
echo "$BACKUP_SCHEDULE . /root/backup.env && sh /scripts/backup.sh >/proc/1/fd/1 2>/proc/1/fd/2" >/etc/crontabs/root
echo "Backup agendado: $BACKUP_SCHEDULE (UTC)."
exec crond -f -l 8
