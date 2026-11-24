# training-service

Dono de: exercícios, restrições de mobilidade, fichas, execuções por série e recordes.

Esqueleto da DEV-0141, com o template da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Projeção de vínculos e clientes (DEV-0039)

- Fila `training.client-events`, durável e ligada a `fithub.client` para `client.link-activated.v1`,
  `client.link-ended.v1` e `client.client-updated.v1`. Consumo idempotente pela `java-commons`; 3 tentativas e
  depois `training.client-events.dlq`. Payload fora do contrato (JSON inválido ou JSON válido com campo faltando, tipo desconhecido ou nome em branco)
  é rejeitado sem voltar para a fila e termina na `.dlq`, sem payload no log e sem contar como processado.
- Só vínculos de personal entram na projeção (BR-03). Encerramento sempre vence: ativação atrasada ou repetida não
  reativa um vínculo encerrado. `ClienteAtualizado` atrasado não desfaz o mais recente.
- **Verificação de acesso por vínculo:** `ClientAccess.requireActiveLink(conta, clientId)`. Todo endpoint com
  `clientId` passa por ela; sem vínculo ativo, 404 (não revela que o cliente existe). Conta com `sub` fora do formato
  UUID também recebe 404.
- `GET /clients/{clientId}` (papel `personal`): dados básicos do cliente vinculado (nome, nascimento, gênero).

## Exercícios (DEV-0053)

- Exercício: nome (2 a 120 caracteres), um ou mais grupos musculares, equipamento, instruções (até 4000) e mídia por
  URL `https` de até 500 caracteres. O FitHub não hospeda vídeo nem imagem.
- Dois tipos: `CATALOG`, comum a todos os personais, e `OWN`, visível só para o dono (BR-13). O dono é o `sub` do
  token (ADR-009).
- Exercício importado guarda fonte, id externo, licença e autor ([ADR-010](../../docs/00-oficial/adr/ADR-010-base-de-exercicios.md));
  a API devolve a atribuição para a tela mostrar o crédito.
- `GET /exercises/{id}` (papel `personal`): exercício do catálogo ou próprio. O de outro personal e o id desconhecido
  respondem o mesmo 404.
- Importação da base (DEV-0054), busca (DEV-0055) e cadastro do exercício próprio (DEV-0056) vêm depois.
