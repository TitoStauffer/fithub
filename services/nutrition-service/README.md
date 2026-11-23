# nutrition-service

Dono de: alimentos (TACO), restrições e preferências alimentares, planos e registro de refeições.

Esqueleto da DEV-0141, com o template da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Projeção de vínculos e clientes (DEV-0151)

- Fila `nutrition.client-events`, durável e ligada a `fithub.client` para `client.link-activated.v1`,
  `client.link-ended.v1` e `client.client-updated.v1`. Consumo idempotente pela `java-commons`; 3 tentativas e
  depois `nutrition.client-events.dlq`. Payload fora do contrato (JSON inválido ou JSON válido com campo faltando, tipo desconhecido ou nome em branco)
  é rejeitado sem voltar para a fila e termina na `.dlq`, sem payload no log e sem contar como processado.
- Só vínculos de nutricionista entram na projeção (BR-03). Encerramento sempre vence: ativação atrasada ou repetida não
  reativa um vínculo encerrado. `ClienteAtualizado` atrasado não desfaz o mais recente.
- **Verificação de acesso por vínculo:** `ClientAccess.requireActiveLink(conta, clientId)`. Todo endpoint com
  `clientId` passa por ela; sem vínculo ativo, 404 (não revela que o cliente existe). Conta com `sub` fora do formato
  UUID também recebe 404.
- `GET /clients/{clientId}` (papel `nutritionist`): dados básicos do cliente vinculado (nome, nascimento, gênero).
