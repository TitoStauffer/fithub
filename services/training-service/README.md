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
- `GET /exercises?name=&muscleGroup=&equipment=&page=&size=` (papel `personal`, DEV-0055): o catálogo mais os
  exercícios próprios de quem busca, nunca os de outro personal (BR-13). O nome busca por trecho, sem diferenciar
  maiúsculas nem acentos (`fold_search` da migration V5, aplicada ao nome gravado e ao texto buscado; `%` e `_` são
  literais). Os filtros se combinam (E lógico). Resposta `{items, total, page, size}`, ordenada por nome; página a
  partir de 0, 20 itens por padrão e até 50. Filtro fora da lista, página negativa ou tamanho fora de 1 a 50 respondem
  400 com o campo em `errors`.
- Cadastro do exercício próprio (DEV-0056) vem depois.

## Importação da base do wger (DEV-0054)

- Copia a API pública do wger (`/exerciseinfo/`, paginada por `limit`/`offset`) para o catálogo local, com o nome e
  as instruções em português quando houver tradução e em inglês quando não. Os ids de músculo, categoria e
  equipamento do wger viram os grupos e equipamentos do FitHub pelo mapa versionado em `WgerMapping`.
- **Desligada por padrão.** Para importar, suba o serviço com a variável abaixo; o job roda uma vez, numa thread à
  parte, depois que o serviço já está no ar:

  ```text
  FITHUB_EXERCISES_IMPORT_ENABLED=true
  ```

  `FITHUB_EXERCISES_IMPORT_BASE_URL` troca a raiz da API (padrão `https://wger.de/api/v2`). Não há endpoint público.
- **Pode rodar de novo:** atualiza pelo id externo (fonte + id), não duplica, não regrava o que não mudou e não apaga
  exercício que saiu da base.
- **Licença (ADR-010):** o texto guarda a licença e o autor da tradução usada (ou os do exercício, quando a tradução
  não tem). A mídia entra só com licença Creative Commons e autor registrados nela, e o crédito dela vai em
  `mediaLicense` e `mediaAuthor` da atribuição (migration V4). Sem mídia assim, o exercício entra sem mídia.
- Item sem nome, sem grupo muscular mapeável ou sem licença e autor do texto é pulado e só contado no log.
- **Falha da base** (erro HTTP, tempo esgotado, resposta fora do formato) interrompe o job; o que já foi importado
  fica como estava e o serviço continua no ar. O log traz só as contagens ou o tipo do erro.
