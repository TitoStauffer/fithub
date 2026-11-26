#!/bin/sh
# Teste de ponta a ponta da observabilidade (DEV-0109), contra o otel-lgtm do compose de desenvolvimento já de pé
# (perfil "observability", com a pasta .logs criada antes de subir). Uso, a partir da raiz:
#   sh infra/observability/observability.test.sh
# Manda um span por OTLP e escreve uma linha de log no formato ECS dos serviços, com o mesmo traceId, e confere que
# o Tempo tem o trace e que o Loki tem a linha, ligada ao trace e só com a mensagem no corpo.
set -eu

grafana="http://127.0.0.1:${GRAFANA_PORT:-3000}"
otlp="http://127.0.0.1:${OTLP_HTTP_PORT:-4318}"
service="fithub-observability-test"
[ -d .logs ] || { echo "A pasta .logs não existe: crie antes de subir o compose." >&2; exit 1; }

trace=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
span=$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')
log=".logs/$service-$trace.log"
trap 'rm -f "$log"' EXIT

fail() {
  echo "FALHOU: $1" >&2
  exit 1
}
# Repete a consulta até ela passar (os componentes gravam em lote), por até 2 minutos.
eventually() {
  attempt=0
  until "$@"; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 60 ] || return 1
    sleep 2
  done
}

end=$(date +%s)
curl -sf -X POST "$otlp/v1/traces" -H 'Content-Type: application/json' -d "{\"resourceSpans\":[{
  \"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"$service\"}}]},
  \"scopeSpans\":[{\"spans\":[{\"traceId\":\"$trace\",\"spanId\":\"$span\",\"name\":\"GET /teste\",\"kind\":2,
    \"startTimeUnixNano\":\"$((end - 1))000000000\",\"endTimeUnixNano\":\"${end}000000000\"}]}]}]}" >/dev/null \
  || fail "o coletor recusou o span por OTLP"

# A mesma forma das linhas da java-commons (logging.structured.format.file=ecs).
printf '%s\n' "{\"@timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\",\"log\":{\"level\":\"INFO\",\"logger\":\"teste\"},\
\"process\":{\"pid\":1,\"thread\":{\"name\":\"main\"}},\"service\":{\"name\":\"$service\"},\
\"message\":\"linha de teste da observabilidade\",\"traceId\":\"$trace\",\"spanId\":\"$span\",\"ecs\":{\"version\":\"8.11\"}}" >"$log"

has_trace() {
  curl -sf "$grafana/api/datasources/proxy/uid/tempo/api/traces/$trace" | grep -q "$service"
}
eventually has_trace || fail "o Tempo não mostrou o trace $trace"

loki_line() {
  curl -sf -G "$grafana/api/datasources/proxy/uid/loki/loki/api/v1/query_range" \
    --data-urlencode "query={service_name=\"$service\"} | trace_id=\"$trace\"" \
    --data-urlencode "since=10m"
}
has_line() {
  loki_line | grep -q '"linha de teste da observabilidade"'
}
eventually has_line || fail "o Loki não mostrou a linha de log com o traceId $trace"
loki_line | grep -q '\\"process\\"' && fail "o corpo da linha no Loki ficou com o JSON inteiro, não só a mensagem"

echo "Observabilidade: OK."
