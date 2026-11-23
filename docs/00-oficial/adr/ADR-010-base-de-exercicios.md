# ADR-010 — Base de exercícios inicial: dados do wger, mídia só com licença explícita

- **Status:** aceita (decisão do Agent na ausência do Tito, para revisão)
- **Data:** 23/11/2026
- **Tarefa:** DEV-0052 (#44), spike da QUESTÃO-07

## Contexto

Sem uma base externa, o personal começa o catálogo vazio (PRD, dependências). A DEV-0054 importa essa base
para uma cópia local no training-service, com um job que pode rodar de novo. A QUESTÃO-07 pergunta se a
licença, o custo e os termos permitem isso num app comercial, inclusive para vídeos e animações.

As fontes avaliadas em 23/11/2026:

| Fonte | Custo | Dados | Mídia | Cópia local |
|---|---|---|---|---|
| ExerciseDB (API v2) | Pago, plano sob consulta | Proprietários, com licença revogável e válida só durante a assinatura | GIFs | **Não:** os termos exigem pedir os dados de novo a cada uso |
| MuscleWiki API | Pago | Proprietários | Vídeos | Depende do plano; termos próprios |
| wger | Gratuito | Creative Commons por exercício (em geral CC-BY-SA 4.0), com autor registrado | Imagens e alguns vídeos, cada um com a própria licença CC | Sim, com atribuição |
| free-exercise-db | Gratuito | Domínio público (Unlicense) | Imagens de origem desconhecida, segundo o próprio mantenedor | Só os dados; as imagens não são seguras para uso comercial |

## Decisão

- **Fonte inicial: o wger.** A importação (DEV-0054) traz nome, grupos musculares, equipamento e instruções,
  com a tradução em português quando existir e o inglês quando não.
- **Atribuição obrigatória.** Cada exercício importado guarda a licença, o autor e a origem (id no wger). A
  tela mostra a atribuição junto do exercício. Mudanças nossas no texto importado continuam sob a mesma
  licença (share-alike vale para os dados, não para o código do FitHub).
- **Mídia só com licença explícita.** Imagem ou vídeo do wger entra apenas com licença CC registrada no
  próprio item, e com a atribuição dele. Item sem licença clara entra sem mídia.
- **Mídia por URL do personal** (DEV-0053) continua sendo o caminho principal para vídeo: o personal informa o
  link do vídeo que pode usar.
- **Fontes pagas com vídeo** (ExerciseDB, MuscleWiki) ficam fora do MVP. Contratar uma delas é decisão de
  custo do Tito; a ExerciseDB, além disso, proíbe a cópia local que a DEV-0054 prevê.

## Alternativas consideradas

- **ExerciseDB:** tem os GIFs que mais se parecem com o fluxo do PRD. Mas custa, a licença acaba com a
  assinatura e os termos proíbem guardar os dados.
- **free-exercise-db:** os dados são domínio público, mas o mantenedor não sabe a origem das imagens. Daria só o
  texto, em inglês, sem mídia. O wger dá o mesmo texto com licença clara e ainda traz mídia licenciada.
- **Começar sem base:** contraria o PRD, porque o catálogo vazio atrasa a primeira ficha do personal.

## Consequências

- O modelo de exercício (DEV-0053) ganha origem, licença, autor e id externo. Exercício próprio do personal
  (DEV-0056) não tem esses campos.
- O job de importação (DEV-0054) guarda o id do wger para atualizar sem duplicar, e descarta mídia sem
  licença.
- Boa parte dos exercícios chega sem vídeo. A tela do catálogo (DEV-0057) e a do treino (DEV-0070) precisam
  funcionar sem mídia, com o link do personal quando houver.
- Se o Tito contratar uma fonte paga depois, ela entra como outra origem, sem trocar o modelo.

## Fontes

- [Termos de uso da ExerciseDB API](https://exercisedb.notion.site/ExerciseDB-API-Terms-of-Use-226983b728ca8090bf7be79564e4b356)
- [README do wger](https://raw.githubusercontent.com/wger-project/wger/master/README.md): código AGPL, dados de
  exercícios sob Creative Commons por item
- [free-exercise-db](https://github.com/yuhonas/free-exercise-db) e a
  [issue sobre a origem das imagens](https://github.com/yuhonas/free-exercise-db/issues/2)
- [Preços da MuscleWiki API](https://api.musclewiki.com/pricing)
