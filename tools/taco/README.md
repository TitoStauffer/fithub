# Conversão da Tabela TACO

Gera o CSV versionado da base de alimentos do nutrition-service a partir da planilha oficial da TACO, 4ª edição
(NEPA/UNICAMP, 2011). As decisões estão no [ADR-011](../../docs/00-oficial/adr/ADR-011-base-de-alimentos-taco.md).

O serviço não baixa nada. O CSV é gerado uma vez, revisado no PR e versionado em
`services/nutrition-service/src/main/resources/taco/taco-4a-edicao.csv`.

## Como refazer

1. Baixe a planilha oficial no site do NEPA (`Taco-4a-Edicao.xlsx`) para uma pasta só dela, fora do repositório.
2. Descompacte o `.xlsx`, que é um zip, em outra pasta vazia.
3. Rode o conversor, que não tem dependências:

   ```bash
   node tools/taco/convert.cjs <pasta descompactada> services/nutrition-service/src/main/resources/taco/taco-4a-edicao.csv
   ```

4. Confira o relatório (itens incompletos e fora das regras do modelo) e revise a etiqueta vegano no PR.

## O que o conversor faz

- Lê a planilha 1 ("CMVCol taco3"): número, descrição, categoria, energia, proteína, carboidrato, lipídeos e fibra.
- Mantém os códigos da legenda ("Tr", "NA", "\*", vazio). Quem converte é o importador do serviço.
- Marca `vegan`. Categorias de origem animal ficam de fora; as outras passam por uma lista de ingredientes
  animais, procurados por palavra inteira, e pelas listas conferidas item a item (pratos prontos e exceções).
