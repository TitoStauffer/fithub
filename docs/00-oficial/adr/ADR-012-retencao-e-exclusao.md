# ADR-012 — Retenção e exclusão dos dados do cliente

- **Status:** aceita (decisão do Agent na ausência do Tito, para revisão)
- **Data:** 25/11/2026
- **Tarefa:** DEV-0107 (#99), QUESTÃO-06

## Contexto

O cliente pode excluir a conta (NFR-01, LGPD art. 18, VI), e cada serviço precisa saber o que apagar, o que manter e
por quanto tempo. O PRD deixou isso em aberto (QUESTÃO-06), junto com o destino das prescrições que o profissional
guardou.

Três regras do PRD orientam a decisão:

- **BR-01:** perfil, saúde, avaliações, fotos e registros de execução pertencem ao cliente.
- **BR-05:** encerrado o vínculo, o profissional mantém só as prescrições que ele mesmo criou.
- **BR-06:** o cliente mantém o histórico depois do vínculo.

O mecanismo está no contrato: o client-service publica o `ContaExcluida` (`client.account-deleted.v1`, desde a
DEV-0105), e cada serviço trata o que tem.

## Decisão

1. **O que é do cliente é apagado de vez** (BR-01), em todos os serviços, ao receber o `ContaExcluida`:
   - **client-service:** perfil, perfil de saúde, avaliações, fotos (inclusive os objetos no bucket), convites
     recebidos, vínculos e consentimentos;
   - **training-service:** sessões e séries registradas, comentários, recordes (inclusive os manuais), ficha própria,
     restrições de mobilidade e as projeções do cliente e dos vínculos;
   - **nutrition-service:** registros de refeição, trocas, desvios e fotos do prato (inclusive os objetos no bucket),
     restrições alimentares e as projeções do cliente e dos vínculos;
   - **Keycloak:** a conta (login).
2. **As prescrições ficam com quem as criou** (BR-05):
   - fichas e planos alimentares, com todas as versões, continuam com o profissional;
   - o id do cliente nelas é trocado por um id aleatório, sem ligação com a pessoa;
   - o nome do cliente vinha da projeção, que é apagada, então nada que identifique o cliente fica nelas.
3. **Prazo:** a exclusão roda quando cada serviço recebe o evento, em minutos. Não há prazo de carência nem
   recuperação da conta.
4. **Backups:**
   - os dumps diários (DEV-0108) guardam o dado excluído até expirarem, em no máximo 30 dias;
   - durante esse prazo, cada serviço guarda só os ids dos clientes excluídos, sem nenhum outro dado, para reaplicar a
     exclusão se um backup for restaurado;
   - depois de 30 dias, a lista de ids também é apagada.
5. **Registros técnicos:**
   - o outbox e o rastro de consumo já têm expurgo (ENGENHARIA, mensageria);
   - os logs não levam dado pessoal (ENGENHARIA §4) e ficam 30 dias (DEV-0109).
6. **Auditoria de acesso de profissionais a dados sensíveis** (o registro de leitura da saúde da DEV-0040 e a
   auditoria da DEV-0106):
   - guardada por 6 meses, o mínimo do Marco Civil da Internet (art. 15) para registros de acesso a aplicação;
   - depois desse prazo, é apagada;
   - a exclusão da conta não apaga a auditoria antes do prazo, porque ela é obrigação legal (LGPD art. 16, I). Ela
     guarda só ids, a ação e a data, nunca o conteúdo.
7. **Fora desta decisão:**
   - **conta parada:** sem expurgo automático no MVP;
   - **conta que também é de profissional:** a exclusão pelo profissional fica fora do MVP. A DEV-0105 define como a
     exclusão do lado cliente trata a conta que também tem cadastro de profissional.

## Alternativas consideradas

- **Anonimizar em vez de apagar o dado do cliente** (manter avaliações e registros sem o id): rejeitada. Medidas,
  fotos e datas juntas identificam a pessoa, e o BR-01 diz que o dado é dela.
- **Carência de 30 dias para desistir:** rejeitada no MVP. Exige estado intermediário em todos os serviços e um
  expurgo agendado; a exclusão é rara e o cliente confirma antes (DEV-0105).
- **Apagar também as prescrições:** rejeitada. O BR-05 diz que elas são do profissional que as criou.
- **Apagar a auditoria junto com a conta:** rejeitada. O registro de acesso é obrigação legal e não guarda o
  conteúdo lido.

## Consequências

- A DEV-0105 implementa os itens 1 a 4: o evento, o consumidor em cada serviço, a anonimização das prescrições, a
  lista de ids e o seu expurgo.
- A DEV-0106 e o registro da DEV-0040 ganham o expurgo de 6 meses.
- A DEV-0108 precisa de backups com retenção de 30 dias, e o teste de restauração reaplica as exclusões da lista.
- A política de privacidade (seção 6) passa a citar estes prazos.
- **O que se perde:** a exclusão não tem volta, e o profissional deixa de saber de quem era a prescrição anonimizada.
  Era esse o objetivo da escolha: o mínimo de dado depois que a pessoa sai.
