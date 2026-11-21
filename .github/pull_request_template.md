# Pull Request

> Título do PR: `[DEV-NNNN] - título objetivo da tarefa`
> Branch: `feature|fix|chore/DEV-NNNN-descricao-curta` (mesma chave do título e da issue)

## 1. Tarefa relacionada

- **Issue:** Closes #N (DEV-NNNN)
- **Tipo de entrega:** Feature / Bugfix / Refactoring / Chore / Documentation
- **Épico (milestone):** E? — nome do épico
- **Agent técnico principal:** Backend / Frontend Web / Mobile / DBA

## 2. Objetivo

Descreva brevemente o problema que esta alteração resolve ou o resultado que pretende alcançar.

## 3. Alterações realizadas

Liste as principais mudanças implementadas.

-
-
-

## 4. Escopo da implementação

### Incluído

-

### Fora do escopo

-

## 5. Decisões técnicas

Descreva as decisões relevantes e suas justificativas. Remova os itens que não se aplicarem.

- **Arquitetura e design:**
- **Serviço(s) afetado(s):** account / client / training / nutrition
- **API (contrato HTTP / OpenAPI):**
- **Eventos (contrato, outbox, consumidores):**
- **Web (React):**
- **Mobile (Expo):**
- **Banco de dados / migrations:**
- **Segurança (autenticação, vínculo, consentimento, dados sensíveis):**
- **Integrações externas:**

## 6. Banco de dados e migrations

Marque o que se aplica e descreva impactos relevantes. PR com migration exige confirmação do Tito antes do merge (AGENTS.md, seção **Merge**).

- [ ] Não houve alteração no banco de dados.
- [ ] As migrations Flyway necessárias foram criadas e revisadas.
- [ ] A compatibilidade e a estratégia de rollback foram avaliadas, quando aplicável.
- [ ] Os impactos sobre dados existentes foram avaliados.

## 7. Estratégia de testes

### Testes executados

Marque somente verificações realmente executadas.

O Agent técnico principal informa os comandos e resultados. O Agent de Product Manager mantém os checkboxes factuais atualizados com base nessas evidências; as aprovações de Code Review e QA são marcadas por seus respectivos agentes na seção 13.

- [ ] Testes unitários (domínio e casos de uso).
- [ ] Testes de arquitetura (ArchUnit).
- [ ] Testes de integração (Testcontainers: banco, RabbitMQ).
- [ ] Testes de API, incluindo autorização por vínculo, quando aplicável.
- [ ] Validação de contrato de eventos, quando aplicável.
- [ ] Testes de frontend web ou mobile, quando aplicável.
- [ ] Build, lint e verificações estáticas.

**Comandos executados:**

```text
# Liste os comandos realmente executados
```

**Resultado:**

Descreva os resultados observados. Não marque verificações que não foram executadas.

**Prova de guarda nova (mutação manual):**

Para cada guarda nova (validação, regra de acesso, invariante): qual mutação foi feita, qual teste a derrubou e a confirmação de que o arquivo voltou ao original. Remova se não houver guarda nova.

## 8. Critérios de aceitação

Copie os critérios da issue. Descreva somente comportamento verificável pelo diff e pelas execuções de CI deste SHA.

- [ ] Critério 1:
- [ ] Critério 2:
- [ ] Critério 3:

## 9. Segurança e qualidade

Marque os itens avaliados e aplicáveis; explique qualquer exceção.

- [ ] O tratamento de erros foi revisado (Problem Details).
- [ ] As entradas e os dados sensíveis (saúde, medidas, fotos) foram tratados adequadamente.
- [ ] Não foram introduzidos segredos ou credenciais no código.
- [ ] Os impactos sobre autenticação, autorização e vínculo foram avaliados.
- [ ] A implementação segue os padrões de código e arquitetura do projeto.
- [ ] Os impactos sobre logs, métricas e observabilidade foram avaliados.

## 10. Evidências

Adicione evidências relevantes para a revisão.

- **Screenshots / vídeos:** quando aplicável.
- **Logs ou resultados de testes:** quando relevantes.
- **Documentação, OpenAPI ou contratos de evento atualizados:** quando aplicável.

## 11. Riscos e limitações

- **Riscos conhecidos:**
- **Limitações:**
- **Dependências ou pendências:**

Se não houver riscos ou limitações conhecidos, declare isso explicitamente.

## 12. Checklist de conclusão

O Agent de Product Manager confere e atualiza este checklist com base nas evidências do Agent técnico principal. Faça a conferência final após Code Review, QA quando o trilho de revisão for completo, e checks configurados, antes do merge.

- [ ] O PR está vinculado à issue correta e a chave é a mesma no título, na branch e na issue.
- [ ] O escopo implementado corresponde à issue.
- [ ] Não foram incluídas alterações não relacionadas.
- [ ] Os testes relevantes foram executados e os resultados registrados.
- [ ] A documentação oficial foi atualizada quando necessário.
- [ ] Não existem conflitos de merge conhecidos.
- [ ] O PR está pronto para revisão.

## 13. Revisão e aprovação

- [ ] **Code Review aprovado** — marcado pelo Agent de Code Review somente após a aprovação.
- [ ] **QA aprovado** — marcado pelo Agent de QA somente após a aprovação.
- **Observações finais:**

> O merge só ocorre com `Merge Readiness` = `READY_TO_MERGE` no HEAD atual, conforme o AGENTS.md. No trilho leve o QA não roda e o seu checkbox permanece desmarcado por construção.
