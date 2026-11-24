# Aprendizados — testes de integração (Testcontainers)

Postgres e RabbitMQ reais nos testes dos serviços, com o contexto do Spring em cache entre as classes.

## pool-de-conexoes-por-contexto

**Regra:** cada combinação diferente de configuração de teste (por exemplo, um `@TestConfiguration` próprio por
classe) sobe outro contexto do Spring, e cada contexto mantém o próprio pool de conexões aberto enquanto fica em
cache. Com o pool padrão (10), poucos contextos já estouram o `max_connections` do Postgres do Testcontainers, e a
classe que sobe por último falha com `FATAL: sorry, too many clients already`, sem relação com o que ela testa. Nos
testes, o pool fica pequeno (`spring.datasource.hikari.maximum-pool-size: 3` em
`src/test/resources/config/application.yml`, que soma à configuração principal em vez de substituí-la).

### Ocorrências

- **Origem:** validação local da DEV-0065 (#57), em 24/11/2026: com a 11ª classe de integração do training-service,
  o `PlanAlertsApiTest` (sem mudança) passou a falhar ao subir o contexto · **PR:** o da DEV-0065
  - **Impacto:** suíte vermelha por falta de conexão, apontando para uma classe que não mudou; o mesmo vai acontecer
    nos outros serviços conforme os testes crescerem.
  - **Correção:** pool de 3 conexões por contexto nos testes do training-service.
  - **Prevenção:** ao criar testes de integração num serviço, conferir se ele já tem o `config/application.yml` de
    teste com o pool pequeno; preferir reaproveitar a mesma configuração de token entre as classes, para o contexto
    ser um só.
