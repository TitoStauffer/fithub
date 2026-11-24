# ADR-011 — Base de alimentos inicial: Tabela TACO em CSV versionado

- **Status:** aceita (decisão do Agent na ausência do Tito, para revisão)
- **Data:** 24/11/2026
- **Tarefa:** DEV-0075 (#67)

## Contexto

A nutricionista monta o plano com alimentos de uma base confiável, com macros por 100 g (PRD FR-35). A fonte
prevista no PRD é a Tabela Brasileira de Composição de Alimentos (TACO), 4ª edição, do NEPA/UNICAMP (2011),
publicada como planilha e PDF no site do NEPA.

O Tito autorizou, em 24/11/2026, baixar a planilha oficial: `Taco-4a-Edicao.xlsx`, em
`nepa.unicamp.br/wp-content/uploads/sites/27/2023/10/` (322 KB, 597 alimentos em 15 categorias). A planilha
traz uma legenda, mas nenhum texto de licença.

## Decisão

- **Fonte inicial: a TACO, 4ª edição.** A importação (DEV-0075) traz nome, energia (kcal), proteína,
  carboidrato, lipídeos e fibra, por 100 g. Na TACO, toda quantidade é por 100 g, então a unidade base é `G`.
- **CSV versionado no serviço.** A planilha é convertida uma vez para
  `services/nutrition-service/src/main/resources/taco/taco-4a-edicao.csv`, revisado no PR. O conversor fica em
  [tools/taco](../../../tools/taco/README.md), para a conversão ser refeita e auditada. O serviço não baixa
  nada em produção.
- **Valores como a planilha publica.** O CSV mantém os códigos da legenda oficial, e o importador os converte:
  - "Tr" (traço, entre 0 e 0,5) vale 0;
  - "NA" (não aplicável, como o carboidrato do óleo) vale 0;
  - "\*" (análise em reavaliação) e vazio (análise não solicitada) ficam ausentes. Só a fibra pode faltar.
    Item sem energia ou sem um dos três macros não entra.
  - O carboidrato da TACO é calculado por diferença (100 menos umidade, proteína, lipídeos e cinzas), e o erro
    analítico deixa quatro itens um pouco abaixo de zero (288, 322, 337 e 400, de -0,05 a -0,01 g). Carboidrato por
    diferença negativo vale 0.
  - Os números são arredondados a 1 casa (kcal) e 2 casas (gramas).
- **O que fica de fora (6 de 597):**
  - em reavaliação: 450 (iogurte), 457 e 458 (leites de vaca);
  - sem análise: 472 (aguardente) e 591 (coco verde);
  - 540, cuja descrição na planilha oficial é só "L".
- **Etiqueta vegano curada no CSV**, item a item. A planilha não tem essa informação.
  - Partida: alimentos das categorias de origem animal não são veganos; nas outras, a descrição não pode citar
    ingrediente animal ou que costuma levar leite, ovo ou gordura animal.
  - Revisão manual: pratos prontos só por lista fechada; exceções nos dois sentidos, como couve-manteiga,
    tofu ("Soja, queijo") e caqui-chocolate, que são veganos, e Maria mole (gelatina) e farinha láctea, que não são.
  - Na dúvida (pão francês, paçoca, chocolate meio amargo), o item fica sem a etiqueta. Etiqueta errada para
    mais é pior do que para menos.
- **Reimportação** pelo par (fonte, número do alimento na TACO): atualiza, não duplica e não apaga. O job fica
  desligado por padrão (`FITHUB_FOODS_IMPORT_ENABLED`), como a importação do wger (ADR-010).
- **Crédito sempre:** cada alimento guarda a fonte "TACO 4ª edição (NEPA/UNICAMP, 2011)" e o número, e a tela
  mostra a origem.

## Consequências

- A base nasce com 591 alimentos brasileiros, sem custo e sem chamada externa.
- **Pendente antes do lançamento (E13):** confirmar com o NEPA, ou no texto do PDF oficial, os termos de uso da
  TACO num produto comercial. A planilha não traz licença, então este ADR não presume uma. Até lá, o crédito à
  fonte é obrigatório em toda exibição.
- A etiqueta vegano é uma curadoria nossa, não da TACO. Corrigir um item é mudar o CSV num PR e reimportar.
- Medidas caseiras (DEV-0076) não vêm da TACO, que não as traz.
