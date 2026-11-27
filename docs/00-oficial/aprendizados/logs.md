# Aprendizados — logs e dados sensíveis

O que pode e o que não pode chegar ao log dos serviços.

## mensagem-de-erro-do-banco-nao-vai-para-o-log

**Regra:** a mensagem de exceção do banco é dado, não diagnóstico. O Postgres põe nela o `Detail`: a linha recusada
(`Failing row contains (...)`) ou a chave repetida (`Key (...)=(...)`). Nas tabelas do FitHub, isso é nome,
nascimento, medidas ou saúde. A defesa fica na origem: o driver sobe com `logServerErrorDetail=false`, padrão da
`java-commons`, e o handler genérico registra do erro de banco só o tipo, o SQLState e a constraint. Como segunda
barreira, todo caminho que registra exceção (Hibernate, consumidor, handler) supõe que a mensagem pode trazer dado.
O teste grava uma linha recusada com um marcador e confere que o marcador não aparece em nenhuma mensagem.

### Ocorrências

- **Validação:** mutante M1 da DEV-0047, que tirou a guarda da bioimpedância do domínio. O CHECK do banco recusou a
  linha, e o log do serviço mostrou a avaliação com peso, circunferências e % de gordura — DEV-0150 (#208) ·
  **PR:** #335
  - **Impacto:** dado de saúde no log, contra o AGENTS.md e o NFR-02, sempre que uma guarda faltar no domínio ou uma
    corrida só for pega pelo banco.
  - **Correção:** o `FithubProblemDetailsHandler` registra do `DataAccessException` só o tipo, o SQLState e a
    constraint, lida só da primeira linha da mensagem.
  - **Prevenção:** `DatabaseErrorLogTest`, com Postgres real e marcador na linha; mutantes de
    `mutations/DEV-0150.json`.
- **Revisão:** Code Review do PR #335, no SHA do merge (MEDIUM não bloqueante) — DEV-0183 (#336) · **PR:** #338
  - **Impacto:** o Hibernate (`SqlExceptionHelper`) e o error handler dos consumidores ainda registravam a mensagem
    com o `Detail`.
  - **Correção:** `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false` como padrão da
    `java-commons`. O driver deixa de pôr o `Detail` na mensagem, e nenhum caminho o recebe.
  - **Prevenção:** `DatabaseErrorDetailTest` confere toda a cadeia de causas, inclusive num batch; o contrato do
    esqueleto confere o valor efetivo no pool de cada serviço; mutantes de `mutations/DEV-0183.json`.
- **Validação:** QA do PR #338 pediu prova de que o batch do Hibernate estava desligado. A guarda mostrou o
  contrário: o Hibernate 7 liga o batch por padrão (o dialeto do Postgres usa 15), e com o `Detail` ligado a mensagem
  da entrada recusada traz o SQL com os valores — DEV-0183 (#336) · **PR:** #338
  - **Impacto:** na `master` anterior, todo erro de banco num batch levava a linha inteira ao log, mesmo sem nenhuma
    configuração de batch no projeto.
  - **Correção:** o mesmo `logServerErrorDetail=false`, que no pgjdbc também tira os parâmetros da mensagem do batch.
  - **Prevenção:** não confiar no padrão de uma biblioteca para afirmar que um recurso está desligado: conferir o valor
    efetivo em execução antes de escrever a afirmação no PR.
