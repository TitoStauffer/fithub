#!/bin/sh
# SeaweedFS em produção (DEV-0110, Code Review do PR #322): uma identidade S3 por serviço, cada uma só com o próprio
# bucket. O client-service não lê as fotos de prato, e o nutrition-service não lê as fotos corporais, mesmo
# comprometidos. As chaves vêm do ambiente e vão para um arquivo só do container; nada fica no repositório.
set -eu
umask 077

check() {
  # Só caracteres que não precisam de escape no JSON.
  value=$(printenv "$1") || { echo "Falta $1." >&2; exit 1; }
  printf '%s' "$value" | grep -Eq '^[A-Za-z0-9+/=_.-]{8,}$' \
    || { echo "$1 precisa ter 8 ou mais caracteres entre letras, números e + / = _ . -" >&2; exit 1; }
  printf '%s' "$value"
}
identity() {
  # $1 = nome, $2 = access key, $3 = secret key, $4 = bucket
  printf '{"name":"%s","credentials":[{"accessKey":"%s","secretKey":"%s"}],' "$1" "$2" "$3"
  printf '"actions":["Read:%s","Write:%s","List:%s","Tagging:%s","Admin:%s"]}' "$4" "$4" "$4" "$4" "$4"
}

client_key=$(check CLIENT_S3_ACCESS_KEY)
client_secret=$(check CLIENT_S3_SECRET_KEY)
nutrition_key=$(check NUTRITION_S3_ACCESS_KEY)
nutrition_secret=$(check NUTRITION_S3_SECRET_KEY)
[ "$client_key" != "$nutrition_key" ] || { echo "As access keys do client e do nutrition precisam ser diferentes." >&2; exit 1; }

{
  printf '{"identities":['
  identity client-service "$client_key" "$client_secret" fithub-photos
  printf ','
  identity nutrition-service "$nutrition_key" "$nutrition_secret" fithub-meal-photos
  printf ']}\n'
} >/tmp/s3-identities.json
# O entrypoint da imagem troca para o usuário seaweed: o arquivo é só dele.
if [ "$(id -u)" = 0 ]; then chown seaweed:seaweed /tmp/s3-identities.json; fi

# Master, volume e filer só no loopback do container: só o gateway S3, que confere as identidades, atende a rede
# (Code Review do PR #322). Sem isso, o filer (8888) e o volume (8080) entregariam qualquer foto sem credencial.
exec /entrypoint.sh server -s3 -dir=/data -master.telemetry=false -s3.config=/tmp/s3-identities.json \
  -ip=127.0.0.1 -ip.bind=127.0.0.1 -s3.ip.bind=0.0.0.0
