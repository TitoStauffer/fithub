# Aprendizados — documentação legal

Política de privacidade, termos de uso e termo de consentimento (`docs/00-oficial/legal/`).

## politica-lista-todo-tratamento

**Regra:** a política de privacidade e o termo de consentimento listam **todo** dado pessoal que o produto trata,
inclusive o que o profissional registra sobre o cliente (restrições, observações) e o que é derivado (recordes). Antes de
escrever ou mudar esses textos, percorra o `estado-atual.md` serviço por serviço e confira cada dado guardado contra a
tabela "Que dados tratamos". Funcionalidade nova que guarda dado pessoal atualiza a política na mesma entrega.

### Ocorrências

- **Revisão:** relatório INCONCLUSIVE do Code Review no PR #287 (SHA `237b51e`) — DEV-0103 (#95) · **PR:** #287
  - **Impacto:** a política e o termo omitiam as restrições alimentares (DEV-0080) e de mobilidade (DEV-0058), dados de
    saúde registrados pelo profissional, e os recordes (DEV-0068). O texto seria publicado sem informar um tratamento
    de dado sensível que já existe.
  - **Correção:** as três linhas entram na tabela da política e no "Além do escopo do termo", com quem registra e quem
    vê cada uma (BR-03).
  - **Prevenção:** esta regra; a varredura pelo `estado-atual.md` faz parte da escrita do texto legal.
