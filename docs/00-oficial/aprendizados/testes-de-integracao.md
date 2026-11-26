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

## teste-de-tela-espera-a-tela-de-destino

**Regra:** quando uma ação muda de rota (salvar e ir para o endereço do registro, por exemplo), o teste de tela
espera a tela de destino terminar de carregar antes de interagir. O sinal é algo que só a tela nova faz: um pedido
que só ela dispara, seguido de `findBy` no elemento dela. Achar um elemento que existe nas duas telas (um alerta,
um aviso) não prova que a navegação terminou, e um `getBy` logo depois disputa a ordem das microtarefas com a
navegação: passa na máquina rápida e falha no runner do CI.

### Ocorrências

- **Bug de validação:** CI do merge da DEV-0067 (330e901) e dos PRs #251 e #261 — DEV-0157 (#252) · **PR:** a
  correção da DEV-0157
  - **Impacto:** o teste "cria o rascunho com exercício do catálogo, mostra o alerta e publica com confirmação"
    (`apps/web/src/features/plans/plans.test.tsx`) falhou ao menos 5 vezes no job Apps. O DOM mostrava só
    "Carregando a ficha…", e o CI dos PRs que rodam o Apps travava sem relação com a mudança.
  - **Correção:** o teste espera o `GET /exercises/{id}`, que só a tela da ficha dispara, e acha o botão
    "Publicar" com `findByRole`.
  - **Prevenção:** a regra acima. A ordem não deu para fixar no mock, porque a disputa é entre microtarefas, então
    a prova é a manual: 20 rodadas seguidas verdes e a leitura do DOM da falha no CI.

## data-de-teste-relativa-ao-relogio-do-codigo

**Regra:** quando o código testado lê o relógio real (um expurgo por prazo, um agendador), a data semeada no teste
é relativa a `Instant.now()`, nunca uma data absoluta. Para comparar com o que volta do banco, trunque para
microssegundos, a precisão do `timestamptz`. Data absoluta só com um relógio fixo injetado no contexto do teste.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #316, no SHA `cca507b` (MEDIUM) — DEV-0172 (#313) · **PR:** #316
  - **Impacto:** o `ReapplyDeletionsTest` do client-service semeava a exclusão em 20/11/2026. O expurgo de 30 dias
    roda com o relógio real antes de reaplicar, então a partir de 20/12/2026 o teste quebraria sozinho e travaria o
    Java Services de todo PR.
  - **Correção:** a data passou a ser `Instant.now().minus(1, DAYS).truncatedTo(MICROS)`, como no training e no
    nutrition.
  - **Prevenção:** esta regra.
