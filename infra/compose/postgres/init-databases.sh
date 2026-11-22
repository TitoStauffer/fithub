#!/bin/sh
# Cria um usuário e um banco por nome em FITHUB_DATABASES (DEV-0012). Roda só na
# primeira subida, com o volume vazio; para recriar, apague o volume (infra/README.md).
set -eu

for name in $FITHUB_DATABASES; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v name="$name" -v password="$FITHUB_DB_PASSWORD" <<'SQL'
CREATE ROLE :"name" LOGIN PASSWORD :'password';
CREATE DATABASE :"name" OWNER :"name";
REVOKE ALL ON DATABASE :"name" FROM PUBLIC;
SQL
done
