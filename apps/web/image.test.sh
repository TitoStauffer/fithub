#!/bin/sh
# Teste da imagem da web (DEV-0176). Constrói a imagem, confere que ela não roda como root e que o nginx serve o app:
# a raiz e uma rota do React Router devolvem o index sem cache, um asset com hash vem com cache longo, e a
# configuração das APIs aponta para os caminhos do mesmo domínio. Uso, a partir da raiz:
#   sh apps/web/image.test.sh
set -eu

image=fithub-web:ci
port=${WEB_IMAGE_TEST_PORT:-18080}
docker build --file apps/web/Dockerfile --tag "$image" .
node tools/repo-checks/check-image-user.mjs "$image"

container=$(docker run -d -p "127.0.0.1:$port:8080" "$image")
trap 'docker rm -f "$container" >/dev/null 2>&1 || true' EXIT
fail() {
  echo "FALHOU: $1" >&2
  docker logs "$container" >&2 || true
  exit 1
}
attempt=0
until curl -sf -o /dev/null "http://127.0.0.1:$port/"; do
  attempt=$((attempt + 1))
  [ "$attempt" -lt 30 ] || fail "o nginx não respondeu"
  sleep 1
done

for path in / /clientes/qualquer-rota; do
  headers=$(curl -sf -D - -o /tmp/web-body "http://127.0.0.1:$port$path") || fail "$path não respondeu 200"
  grep -q '<div id="root">' /tmp/web-body || fail "$path não devolveu o index do app"
  echo "$headers" | grep -qi '^cache-control: no-cache' || fail "$path veio sem Cache-Control: no-cache"
done

asset=$(grep -o '/assets/[^"]*\.js' /tmp/web-body | head -1)
[ -n "$asset" ] || fail "o index não cita nenhum asset"
curl -sf -D - -o /tmp/web-asset "http://127.0.0.1:$port$asset" | grep -qi '^cache-control: public, max-age=31536000, immutable' \
  || fail "o asset $asset veio sem o cache longo"
# Os endereços das APIs e do Keycloak foram embutidos como caminhos (src/lib/config.ts resolve na origem da página).
for path in /api/account /api/client /api/training /api/nutrition /auth; do
  # O minificador escolhe as aspas (", ' ou crase).
  grep -Eq "[\"'\`]$path[\"'\`]" /tmp/web-asset || fail "o bundle não aponta para $path"
done
curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/assets/inexistente.js" | grep -q 404 \
  || fail "um asset que não existe não devolveu 404"
# Caminhos do Keycloak, das APIs e dos buckets que chegam à web (o console de administração, por exemplo) dão 404,
# não o index do app.
for path in /auth/admin/ /auth/realms/master/ /api/account/x /fithub-photos/x.jpg; do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port$path")" = 404 ] \
    || fail "$path não devolveu 404 na web"
done
# Uma rota do app que só começa com as mesmas letras continua no app.
curl -sf -o /dev/null "http://127.0.0.1:$port/authorizations" || fail "/authorizations não devolveu o app"

echo "Imagem da web: OK."
