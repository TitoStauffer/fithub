# ADR-009 — O `sub` do Keycloak é o id da pessoa em todos os serviços

- **Status:** aceita (decisão do Agent na ausência do Tito, para revisão)
- **Data:** 23/11/2026
- **Tarefa:** DEV-0149 (#204)

## Contexto

Os serviços conversam por eventos (ADR-003) e recebem pedidos com o token do Keycloak (ADR-004). Para
conferir um vínculo ou saber quem convida, um serviço precisa ligar a pessoa do token à pessoa dos eventos.

Até a DEV-0030, os ids eram dois:

- o `ClienteAtualizado` usava como `clientId` o `sub` da conta (DEV-0034);
- o `ProfissionalCadastrado` levava um `professionalId` próprio, gerado pelo account-service (UUID v7), sem
  a conta.

Com isso, nenhum serviço conseguia saber qual profissional fazia um pedido. Isso travava o convite
(DEV-0035) e a verificação de vínculo no training e no nutrition (DEV-0039).

## Decisão

O `sub` da conta no Keycloak é o id da pessoa em todos os serviços:

- o `clientId` e o `professionalId` são o `sub`;
- a mesma conta pode ser cliente e profissional (QUESTÃO-02), e então os dois ids são o mesmo valor;
- regras que dependem disso, como não aceitar o próprio convite, conferem esse caso;
- no account-service, o id do profissional é o da conta (`Professional.idOf`), garantido pelo domínio e por
  um CHECK no banco (V4).

## Alternativas consideradas

- **Claim `professional_id` no token:** exige atributo no perfil de usuário do Keycloak, mapper no realm e
  mais um passo na atribuição do papel. É mais um ponto de falha na emissão do token, sem ganho.
- **Evento v2 com `accountId`:** deixaria dois ids para a mesma pessoa, que todo consumidor precisaria
  traduzir, e criaria uma versão nova de evento sem consumidor nenhum ainda.

## Consequências

- Os serviços reconhecem a pessoa direto pelo token, sem projeção da conta.
- O `sub` circula nos eventos e nas projeções dos outros serviços. Ele é um identificador opaco
  (pseudônimo); dados do cadastro, como o registro no conselho (CREF/CRN), continuam sem sair do serviço
  dono.
- O contrato v1 do `ProfissionalCadastrado` não muda (o id continua sendo um UUID).
- Trocar de provedor de identidade exigiria migrar os ids em todos os serviços. Isso é aceitável, porque a
  ADR-004 já fixa o Keycloak.
