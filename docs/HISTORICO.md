# Documentação histórica

A documentação da primeira versão do FitHub foi removida da árvore na DEV-0010
(decisão do Tito em 22/11/2026): foi superada por [`docs/00-oficial`](00-oficial/README.md)
e seus caminhos longos quebravam o checkout no Windows.

Nada se perdeu: tudo continua no histórico do git. O último commit da `master` que
ainda contém esses arquivos é **`9b8f73a`**.

## O que foi removido

| Caminho | O que era |
|---|---|
| `docs/00-engineering/` | Handbook de engenharia e índice de ADRs (rascunhos) |
| `docs/00-knowledge base/` | Knowledge base da primeira versão (visão, decisões de MVP, domínios) |
| `docs/01-playbook/` | Playbooks, AI kit, bootstrap e arquitetura por domínio |
| `docs/01-product/`, `docs/03-platform/`, `docs/04-standards/` | Esqueletos vazios |
| `docs/02-domains/` | Workshop do domínio Identity |
| `docs/02-use-cases/` | ~1.180 arquivos de casos de uso por domínio |
| `docs/03-specification-gold/` | Especificação "gold" por domínio |
| `docs/DDD_MODELING.md`, `docs/specification-manifest.yaml` | Modelagem DDD do MVP antigo e manifesto |
| `playbook/` | Cópia antiga do playbook |
| `MVP_SPECIFICATION.md`, `API_DOCUMENTATION.md`, `IMPROVEMENTS.md`, `REPOSITORY_LAYER.md`, `SETUP.md`, `TESTS.md`, `FRONTEND_README.md` | Documentos da raiz da primeira versão |

## Como recuperar

Ler um arquivo sem trazê-lo de volta:

```powershell
git show 9b8f73a:MVP_SPECIFICATION.md
```

Restaurar uma pasta na sua cópia (sem commitar):

```powershell
git checkout 9b8f73a -- "docs/00-knowledge base"
```

No Windows, pastas com caminhos longos (`docs/02-use-cases/...`) exigem
`git config core.longpaths true` antes de restaurar.
