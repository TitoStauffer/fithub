# Documentação oficial do FitHub

**Status:** ativo
**Última revisão:** 21/11/2026

Fonte única da verdade do projeto e **vault de contexto dos agentes** de Code
Review e QA (DEV-0119): o reviewer lê este índice, o
[estado atual](estado-atual.md) e as notas que o
[`context-map.json`](context-map.json) associa aos caminhos alterados no PR.
Código, migrations, workflows e testes continuam sendo a evidência executável.

## Como usar

1. Leia este índice e o [estado atual](estado-atual.md).
2. Abra só as notas ligadas à tarefa.
3. Confirme no código antes de concluir algo.
4. Atualize a nota afetada na mesma entrega que muda o comportamento descrito.
5. Nunca registre segredos, tokens, dados pessoais ou valores reais de ambiente.

## Documentos

| Documento | Conteúdo |
|---|---|
| [PRD.md](PRD.md) | Produto: problema, usuários, requisitos (FR), regras de negócio (BR), escopo, critérios de aceitação |
| [ENGENHARIA.md](ENGENHARIA.md) | Stack, serviços, comunicação por eventos, segurança, testes, CI/CD, workflow e método |
| [BACKLOG.md](BACKLOG.md) | Épicos e tarefas `DEV-NNNN` |
| [estado-atual.md](estado-atual.md) | O que já existe e o que ainda não |
| [aprendizados/](aprendizados/README.md) | Problemas confirmados em Code Review e como evitá-los |

As regras de trabalho (git, PR, merge, papéis, revisão) estão no
`AGENTS.md` da raiz.

## Manutenção do mapa de contexto

O [`context-map.json`](context-map.json) liga caminhos do repositório às notas
que os agentes devem ler. Ao criar um serviço, app ou área nova, acrescente a
rota correspondente na mesma entrega.
