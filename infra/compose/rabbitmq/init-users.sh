#!/bin/sh
# Um usuário do RabbitMQ por serviço em produção (DEV-0110, Code Review do PR #322), criado pelo administrador do
# broker, que fica só para a operação. Idempotente: a cada deploy, recria a senha e as permissões.
# Cada serviço:
#   - publica (write) só na própria exchange, fithub.<serviço>, e nas próprias filas (<serviço>.*);
#   - declara (configure) só as próprias filas e a própria exchange. As quatro exchanges são declaradas aqui, pelo
#     administrador, e os consumidores não as declaram (FITHUB_MESSAGING_DECLARE_SOURCE_EXCHANGES=false): no RabbitMQ,
#     configure também vale para apagar, e um serviço comprometido apagaria a exchange de outro (DEV-0177);
#   - lê (read) só as próprias filas e as exchanges dos serviços, para ligar as filas a elas.
# Assim, um serviço comprometido não publica evento em nome de outro (um VinculoAtivado forjado, por exemplo) nem lê
# a fila de outro. Variáveis: RABBITMQ_ADMIN_USER, RABBITMQ_ADMIN_PASSWORD e <SERVIÇO>_RABBITMQ_PASSWORD.
set -eu

exchanges='fithub\.(account|client|training|nutrition)'
admin() {
  rabbitmqadmin --non-interactive --host rabbitmq --port 15672 \
    --username "$RABBITMQ_ADMIN_USER" --password "$RABBITMQ_ADMIN_PASSWORD" "$@"
}

# A API de gestão sobe depois do healthcheck do broker: até 2 minutos de espera.
attempt=0
until admin show overview >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  [ "$attempt" -lt 60 ] || { echo "A API de gestão do RabbitMQ não respondeu." >&2; exit 1; }
  sleep 2
done

for service in account client training nutrition; do
  admin declare exchange --name "fithub.$service" --type topic --durable true >/dev/null
  echo "Exchange fithub.$service pronta."
done

for service in account client training nutrition; do
  variable="$(echo "$service" | tr '[:lower:]' '[:upper:]')_RABBITMQ_PASSWORD"
  password=$(printenv "$variable") || { echo "Falta $variable." >&2; exit 1; }
  [ -n "$password" ] || { echo "Falta $variable." >&2; exit 1; }
  admin declare user --name "$service" --password "$password" --tags "" >/dev/null
  admin declare permissions --user "$service" \
    --configure "^($service\..*|fithub\.$service)\$" \
    --write "^($service\..*|fithub\.$service)\$" \
    --read "^($service\..*|$exchanges)\$" >/dev/null
  echo "Usuário $service do RabbitMQ pronto."
done
