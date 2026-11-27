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
- **Revisão:** Code Review APPROVED do PR #348, no SHA `9ffef79` (LOW) — DEV-0165 (#297) · **PR:** #348
  - **Impacto:** o `ClientEventsConsumerTest` do training e do nutrition semeava a lista `deleted_account` e os eventos
    em 23/11/2026. O expurgo da lista roda na subida do contexto e a cada 6 h, numa thread própria; a partir de
    23/12/2026, uma rodada no meio do teste apagaria a linha e faria o teste falhar às vezes.
  - **Correção:** os instantes do teste (exclusão e eventos) são relativos a `Instant.now().truncatedTo(MICROS)`.
  - **Prevenção:** vale também para o instante dentro do payload de evento, não só para a linha semeada no banco.

## teste-nao-apaga-dado-do-banco-compartilhado

**Regra:** as classes de teste de um serviço dividem o mesmo banco (Testcontainers reaproveitado entre os contextos).
Um teste não apaga as linhas de uma tabela inteira para começar do zero: isso tira o dado que outra classe gravou e
quebra o que ela confere depois. Para contagens e agregados, o teste mede o acréscimo sobre o banco: lê antes, grava
os próprios dados com ids novos e lê de novo.

### Ocorrências

- **Validação local:** build completo da DEV-0113 (#105), antes do PR · **PR:** o da DEV-0113
  - **Impacto:** o `SubscriptionMetricsTest` apagava todos os profissionais. Os eventos de cadastro gravados no outbox
    por outra classe ficaram órfãos, e o `ProfessionalRegistrationTest.cadastroDesfeitoNaoDeixaNemOCadastroNemOEvento`,
    que conta evento órfão do último minuto, falhou.
  - **Correção:** o teste das métricas confere o acréscimo, sem apagar nada, como os das outras métricas.
  - **Prevenção:** esta regra.

## cada-condicao-da-consulta-tem-mutante

**Regra:** numa consulta que define uma regra (métrica, filtro de acesso, expurgo), cada condição do `where` e do
`having` é uma guarda: a janela de tempo, o estado, o tipo e o "sem outro vínculo ativo". Antes do PR, liste as
condições de cada consulta e declare um mutante para cada uma no manifesto da tarefa, mesmo quando o teste já tem o
caso de limite. Sem o mutante, o PR não prova que o teste pega a condição.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #326, no SHA `0a97e3e` (MEDIUM) — DEV-0113 (#105) · **PR:** #326
  - **Impacto:** sete condições das métricas tinham teste, mas não tinham mutante. Eram a recusa de nome fora de
    `fithub.*`, o estado expirado, o "sem vínculo ativo" nos dois serviços, o dia do fim do vínculo na nutrição e as
    janelas de 30 dias. O critério de aceitação afirmava uma cobertura que não estava provada.
  - **Correção:** mutantes M11 a M18 de `mutations/DEV-0113.json`.
  - **Prevenção:** esta regra.
- **Revisão:** Code Review CHANGES_REQUESTED do PR #326, no SHA `6ac1ced` (2 MEDIUM) — DEV-0113 (#105) · **PR:** #326
  - **Impacto:** a correção da rodada anterior cobriu só as condições apontadas. Sobraram condições sem teste:
    - o vínculo ativo nos vinculados da nutrição;
    - a janela do uso depois do vínculo na nutrição;
    - a janela de "profissionais que publicaram".

    Sobraram também condições com teste, mas sem mutante: o estado ativo, o tipo e o status nos dois lados.
  - **Correção:**
    - uma varredura de todas as condições de todas as consultas: 33 condições, cada uma com caso de teste e mutante
      (M19 a M30 nesta rodada);
    - a condição que nenhum teste consegue provar saiu, com o motivo no código: o autor em "profissionais que
      publicaram" do treino, que o `count(distinct)` de um id nulo já exclui.
  - **Prevenção:** a varredura é da consulta inteira, não do achado. Faça uma tabela condição, caso de teste e mutante
    antes de pedir revisão.
