#!/bin/sh
# Teste de fumaça do compose de produção (DEV-0110), sem os quatro serviços (as imagens deles estão no GHCR). Sobe o
# Traefik, o Keycloak em modo de produção, o SeaweedFS, o backup e o otel-lgtm com valores de teste, e confere a borda
# com o certificado padrão do Traefik (o Let's Encrypt não emite para fithub.localhost). Uso, a partir da raiz:
#   sh infra/compose/prod-smoke.test.sh
# Portas: FITHUB_HTTP_PORT e FITHUB_HTTPS_PORT (padrão 8880 e 8443), GRAFANA_PORT e KEYCLOAK_ADMIN_PORT.
set -eu

domain=fithub.localhost
http=${FITHUB_HTTP_PORT:-8880}
https=${FITHUB_HTTPS_PORT:-8443}
work=$(mktemp -d)
env_file="$work/.env"
# Valores só deste teste; o servidor ACME de staging, para não chamar o de produção.
sed -e "s/^FITHUB_DOMAIN=.*/FITHUB_DOMAIN=$domain/" -e 's/<[a-z-]*>/teste-smoke/g' infra/compose/.env.prod.example >"$env_file"
printf 'ACME_CA_SERVER=https://acme-staging-v02.api.letsencrypt.org/directory\nFITHUB_HTTP_PORT=%s\nFITHUB_HTTPS_PORT=%s\n' \
  "$http" "$https" >>"$env_file"
compose="docker compose -p fithub-smoke -f infra/compose/docker-compose.prod.yml --env-file $env_file"
export FITHUB_IMAGE_TAG=0000000000000000000000000000000000000000
cleanup() {
  $compose down -v >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

fail() {
  echo "FALHOU: $1" >&2
  $compose ps -a >&2 || true
  $compose logs --tail 30 traefik keycloak otel-lgtm >&2 || true
  exit 1
}
edge() {
  curl -sk --resolve "$domain:$https:127.0.0.1" "$@"
}

$compose up -d --wait --wait-timeout 420 traefik keycloak seaweedfs backup otel-lgtm || fail "o compose não subiu saudável"

# HTTP vira HTTPS.
location=$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' --resolve "$domain:$http:127.0.0.1" "http://$domain:$http/auth/")
case "$location" in 30[178]\ https://$domain*) ;; *) fail "HTTP não redirecionou para HTTPS: $location" ;; esac

# O realm fithub responde pela borda, com o emissor público (o que os serviços conferem no token). O Keycloak fica
# saudável antes de terminar de importar o realm e responde 503 nesse meio-tempo: até 2 minutos de novas tentativas.
discovery=$(edge -f --retry 30 --retry-delay 4 --retry-all-errors \
  "https://$domain:$https/auth/realms/fithub/.well-known/openid-configuration") \
  || fail "o realm fithub não respondeu pela borda"
echo "$discovery" | grep -q "\"issuer\":\"https://$domain/auth/realms/fithub\"" || fail "o emissor do realm não é o endereço público"
edge -D - -o /dev/null "https://$domain:$https/auth/realms/fithub/.well-known/openid-configuration" \
  | grep -qi '^strict-transport-security: max-age=31536000' || fail "a borda não mandou o HSTS"

# O console de administração e o realm master ficam fora da borda.
for path in /auth/admin/ /auth/realms/master/.well-known/openid-configuration /auth/; do
  [ "$(edge -o /dev/null -w '%{http_code}' "https://$domain:$https$path")" = 404 ] || fail "a borda expôs $path"
done

# O bucket das fotos chega ao SeaweedFS com o caminho inteiro (resposta do S3, não do Traefik).
edge "https://$domain:$https/fithub-photos/inexistente.jpg" | grep -q '<Error>' || fail "o bucket de fotos não chegou ao S3"

# Cada API tem a sua rota: sem o serviço de pé, o Traefik responde 502 (rota existe), não 404.
for service in account client training nutrition; do
  code=$(edge -o /dev/null -w '%{http_code}' "https://$domain:$https/api/$service/actuator/health")
  [ "$code" = 502 ] || fail "a rota /api/$service respondeu $code, esperava 502 sem o serviço"
done

# O backup está agendado e o job roda com o ambiente do container.
$compose exec -T backup grep -q '^0 3 \* \* \* .*backup.sh' /etc/crontabs/root || fail "o backup não ficou agendado"
$compose exec -T backup sh -c '. /root/backup.env && sh /scripts/backup.sh' >/dev/null || fail "o job do backup falhou"
$compose exec -T backup sh -c 'ls /backups/*/COMPLETE' >/dev/null || fail "o job do backup não gravou um backup completo"

echo "Compose de produção (fumaça): OK."
