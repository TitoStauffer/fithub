# ADR-006 — SeaweedFS como S3 compatível no ambiente local

**Status:** aceito · **Data:** 22/11/2026 · **Decisor:** Agent técnico (autonomia delegada pelo Tito)

## Contexto

O ENGENHARIA.md previa MinIO como storage S3 compatível no ambiente local (fotos de progresso,
exames, anexos), com Cloudflare R2 ou similar em produção. Ao montar o compose de desenvolvimento
(DEV-0012), a imagem `minio/minio` não existe mais no Docker Hub e a do quay.io exige
autenticação: a edição comunitária deixou de ser distribuída como imagem pronta.

## Decisão

O compose local usa **SeaweedFS** (`chrislusf/seaweedfs`, versão fixada) em modo `server -s3`, com
uma credencial de desenvolvimento por variável de ambiente e acesso anônimo negado. Os serviços
falam só a API S3 (AWS SDK), sem nada específico do SeaweedFS; produção continua com um S3
gerenciado, como no ENGENHARIA.md.

## Alternativas consideradas

- **MinIO de uma tag antiga ou compilado do código:** sem atualizações de segurança e com build
  próprio para manter.
- **RustFS:** API e configuração parecidas com o MinIO, mas projeto mais novo (2023).
- **Garage:** leve, mas exige configuração de layout do cluster antes do primeiro uso.

## Consequências

- O ambiente local não depende de imagem descontinuada; o SeaweedFS é Apache-2.0 e mantido desde 2014.
- Diferenças finas de comportamento S3 entre local e produção ficam possíveis; testes de integração
  de storage devem usar só operações comuns (put, get, delete, URL pré-assinada).
- A telemetria anônima do SeaweedFS fica desligada no compose.
