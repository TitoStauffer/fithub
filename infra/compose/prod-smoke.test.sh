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
# Valores só deste teste, um diferente por variável (smoke-<NOME>), para as senhas e as identidades do S3 não
# coincidirem; o servidor ACME de staging, para não chamar o de produção.
sed -e "s/^FITHUB_DOMAIN=.*/FITHUB_DOMAIN=$domain/" -e 's/^\([A-Z0-9_]*\)=<[a-z-]*>$/\1=smoke-\1/' \
  -e 's/<[a-z-]*>/teste-smoke/g' infra/compose/.env.prod.example >"$env_file"
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

$compose up -d --wait --wait-timeout 420 traefik keycloak seaweedfs rabbitmq backup otel-lgtm || fail "o compose não subiu saudável"
# O rabbitmq-users roda e termina: no deploy, os serviços dependem dele (service_completed_successfully), e o
# up --wait aceita a saída 0; sem os serviços, ele roda aqui pelo run.
$compose run --rm rabbitmq-users >/dev/null || fail "o rabbitmq-users não criou os usuários dos serviços"

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

# O console de administração abre pelo túnel (127.0.0.1), com o endereço do túnel, não o domínio público.
admin_port=${KEYCLOAK_ADMIN_PORT:-8080}
console=$(curl -sf "http://127.0.0.1:$admin_port/auth/admin/master/console/") || fail "o console do Keycloak não abriu pelo 127.0.0.1"
echo "$console" | grep -q "http://localhost:$admin_port/auth" || fail "o console não usa o endereço do túnel"
echo "$console" | grep -q "https://$domain/auth/admin" && fail "o console manda o login para o domínio público"

# O bucket das fotos chega ao SeaweedFS com o caminho inteiro (resposta do S3, não do Traefik).
edge "https://$domain:$https/fithub-photos/inexistente.jpg" | grep -q '<Error>' || fail "o bucket de fotos não chegou ao S3"

# Cada identidade S3 só alcança o próprio bucket, pela borda (as URLs pré-assinadas passam por ela).
s3() {
  # $1 = serviço (CLIENT ou NUTRITION), $2 = método, $3 = caminho. Devolve o status HTTP.
  # Criar o bucket (caminho com / no fim) vai sem corpo; gravar um objeto leva um corpo qualquer.
  case "$2 $3" in PUT\ */) body= ;; PUT\ *) body=foto ;; *) body= ;; esac
  if [ -n "$body" ]; then
    edge -o /dev/null -w '%{http_code}' -X PUT --data-binary "$body" --aws-sigv4 "aws:amz:us-east-1:s3" \
      --user "smoke-$1_S3_ACCESS_KEY:smoke-$1_S3_SECRET_KEY" "https://$domain:$https$3"
  else
    edge -o /dev/null -w '%{http_code}' -X "$2" --aws-sigv4 "aws:amz:us-east-1:s3" \
      --user "smoke-$1_S3_ACCESS_KEY:smoke-$1_S3_SECRET_KEY" "https://$domain:$https$3"
  fi
}
expect() {
  # $1 = status esperado, $2 = mensagem, o resto = argumentos do s3.
  expected=$1
  message=$2
  shift 2
  got=$(s3 "$@")
  [ "$got" = "$expected" ] || fail "$message (status $got, esperava $expected)"
}
expect 200 "o client-service não criou o próprio bucket" CLIENT PUT /fithub-photos/
expect 200 "o nutrition-service não criou o próprio bucket" NUTRITION PUT /fithub-meal-photos/
expect 200 "o client-service não gravou a foto" CLIENT PUT /fithub-photos/corpo.jpg
expect 403 "o nutrition-service leu a foto corporal" NUTRITION GET /fithub-photos/corpo.jpg
expect 403 "o nutrition-service apagou a foto corporal" NUTRITION DELETE /fithub-photos/corpo.jpg
expect 403 "o client-service gravou no bucket de prato" CLIENT PUT /fithub-meal-photos/prato.jpg

# Cada serviço tem o seu usuário no RabbitMQ: publica na própria exchange e não publica na de outro (um
# VinculoAtivado forjado). A API de gestão só atende usuário com a tag management, dada aqui só para o teste; as
# permissões nas exchanges valem igual pelo AMQP.
broker() {
  $compose exec -T rabbitmq rabbitmqadmin --non-interactive --host localhost "$@" >/dev/null 2>&1
}
admin="--username smoke-RABBITMQ_ADMIN_USER --password smoke-RABBITMQ_ADMIN_PASSWORD"
nutrition="--username nutrition --password smoke-NUTRITION_RABBITMQ_PASSWORD"
# shellcheck disable=SC2086 # as credenciais são argumentos separados.
broker $admin declare exchange --name fithub.client --type topic || fail "o administrador não declarou a exchange"
# shellcheck disable=SC2086
broker $admin declare exchange --name fithub.nutrition --type topic || fail "o administrador não declarou a exchange"
# shellcheck disable=SC2086
broker $admin declare user --name nutrition --password smoke-NUTRITION_RABBITMQ_PASSWORD --tags management \
  || fail "não deu a tag de teste ao usuário nutrition"
# shellcheck disable=SC2086
broker $nutrition publish message --exchange fithub.nutrition --routing-key nutrition.teste.v1 --payload '{}' \
  || fail "o nutrition-service não publicou na própria exchange"
# shellcheck disable=SC2086
broker $nutrition publish message --exchange fithub.client --routing-key client.link-activated.v1 --payload '{}' \
  && fail "o nutrition-service publicou na exchange do client-service"

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
