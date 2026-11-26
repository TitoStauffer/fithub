#!/bin/sh
# Cria um usuário e um banco por nome em FITHUB_DATABASES (DEV-0012). Roda só na
# primeira subida, com o volume vazio; para recriar, apague o volume (infra/README.md).
# A senha de cada usuário vem de <NOME>_DB_PASSWORD (por exemplo CLIENT_DB_PASSWORD), exigida em
# produção (DEV-0110): cada serviço recebe só a sua. Sem ela, vale FITHUB_DB_PASSWORD, a senha única
# do compose de desenvolvimento.
set -eu

for name in $FITHUB_DATABASES; do
  variable="$(echo "$name" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
  password=$(printenv "$variable" || true)
  if [ -z "$password" ]; then
    password=${FITHUB_DB_PASSWORD:?defina $variable ou FITHUB_DB_PASSWORD}
  fi
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v name="$name" -v password="$password" <<'SQL'
CREATE ROLE :"name" LOGIN PASSWORD :'password';
CREATE DATABASE :"name" OWNER :"name";
REVOKE ALL ON DATABASE :"name" FROM PUBLIC;
SQL
done
