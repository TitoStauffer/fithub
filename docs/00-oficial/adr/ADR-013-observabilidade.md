# ADR-013 — Observabilidade: logs e traces centralizados

- **Status:** aceita (decisão do Agent na ausência do Tito, para revisão)
- **Data:** 26/11/2026
- **Tarefa:** DEV-0109 (#101)

## Contexto

Desde a DEV-0013, cada serviço escreve logs JSON (formato ECS) com `traceId` e `spanId`, mas nada é coletado: para
seguir uma requisição entre serviços, é preciso abrir o log de cada um. O ENGENHARIA (seção 7) previa Grafana com
Loki e Tempo no épico de operação, em container ou em plano gratuito de nuvem.

Duas restrições pesam:

- **sem custo e sem conta externa;**
- **nenhum dado pessoal ou de saúde novo** fora do serviço (NFR-01, ADR-012).

O caminho principal entre serviços é assíncrono: o evento é gravado no outbox na transação do dado e publicado
depois, por outra thread (ADR-003). Sem cuidado, o trace da requisição termina no outbox, e o consumidor começa
outro.

## Decisão

1. **Pilha:** a imagem `grafana/otel-lgtm`, com versão fixa (`0.40.0`). Ela traz, num container só:
   - o coletor OpenTelemetry, com a configuração do FitHub em `infra/observability/otelcol-config.yaml`;
   - Loki, Tempo, Prometheus e Grafana.

   No compose de desenvolvimento, ela fica no perfil `observability`, porque a imagem tem 2,8 GB. O compose de
   produção é da DEV-0110.
2. **Traces:**
   - os serviços exportam os spans por OTLP/HTTP com o exportador oficial `io.opentelemetry:opentelemetry-exporter-otlp`,
     na versão gerenciada pelo Spring Boot, que vem pela java-commons;
   - o exportador só existe com `management.opentelemetry.tracing.export.otlp.endpoint` configurado; sem ele, nada
     sai do serviço, e testes e CI não mudam;
   - a amostragem continua em 100% (`management.tracing.sampling.probability`), porque o volume do MVP é pequeno.
3. **Logs:**
   - o coletor lê os arquivos de log JSON dos serviços (`logging.file.name`, no mesmo formato ECS do console, padrão
     da java-commons) e manda para o Loki;
   - cada linha leva o `traceId` e o `spanId`, que ligam o log ao trace no Tempo, e fica só com a mensagem no corpo.
4. **Trace pelo RabbitMQ:**
   - o outbox grava, junto com o evento, o `traceparent` W3C do span atual (coluna `trace_parent`, migration V2 da
     java-commons);
   - o publicador manda esse valor no cabeçalho `traceparent` da mensagem;
   - a observação dos listeners (`spring.rabbitmq.listener.simple.observation-enabled`, padrão da java-commons) lê o
     cabeçalho, e o consumidor continua o mesmo trace. O publicador não abre span próprio.
5. **Dados nos spans:** só os atributos padrão do Spring (método, rota, status, duração, nome da exceção e, nos
   listeners, fila e exchange). Corpo e parâmetro de consulta não entram. As observações do Spring Security ficam
   desligadas pela java-commons (`management.observations.enable.spring.security=false`): elas criavam um span por
   filtro, com os papéis da pessoa (`spring.security.authentication.authorities`), o que nem os logs têm. O `http.url` do servidor é o caminho sem a
   consulta, e os ids no caminho são os mesmos que já estão nos logs. Os clientes HTTP dos serviços (Keycloak, wger)
   são montados sem o registro de observação, então não geram span. O `traceparent` gravado no outbox tem só
   identificadores aleatórios.

## Alternativas consideradas

- **Ponte do Logback para o OpenTelemetry (logs por OTLP):** só existe como versão `-alpha` e pede uma versão da API
  do OpenTelemetry diferente da que o Spring Boot gerencia. Ler o arquivo de log não muda nada no serviço.
- **Ler a saída dos containers (Docker):** no desenvolvimento, os serviços rodam fora do Docker, e não há saída de
  container para ler. O arquivo de log serve aos dois ambientes.
- **Observação do `RabbitTemplate`:** o publicador roda numa thread agendada, sem o trace da requisição, e o span dele
  começaria um trace novo. O `traceparent` gravado com o evento preserva o trace de origem.
- **Plano gratuito de nuvem (Grafana Cloud):** pede conta externa e manda logs com dado pessoal para fora da VPS.
- **Pilhas separadas (Loki, Tempo e Grafana em containers próprios):** mais configuração e mais imagens para manter,
  sem ganho no volume do MVP.

## Consequências

- Uma requisição é seguida no Grafana de ponta a ponta: o trace no Tempo, passando pelo RabbitMQ, e os logs de cada
  serviço no Loki, pelo `traceId`.
- O evento publicado sem trace (uma tarefa agendada, um consumidor que republica fora de uma requisição) vai sem o
  cabeçalho, e o consumidor começa um trace novo.
- A mensagem de exceção registrada no span é a mesma que já vai para o log (débito #208, DEV-0150, para a linha
  recusada pelo banco).
- **Retenção:** o Loki guarda dados pessoais dos logs e precisa da mesma retenção de 30 dias dos logs (ADR-012). No
  desenvolvimento, o container não tem volume, e os dados somem quando ele é removido. A DEV-0110 configura a retenção
  no compose de produção.
- O Grafana do compose de desenvolvimento entra sem login e fica só em `127.0.0.1`. Em produção, o acesso é da DEV-0110.
