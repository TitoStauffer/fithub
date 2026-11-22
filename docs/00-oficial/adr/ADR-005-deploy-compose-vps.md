# ADR-005 — Deploy inicial com Docker Compose em VPS

**Status:** aceito · **Data:** 21/11/2026 · **Decisor:** Tito

## Contexto

Quatro serviços, Keycloak, RabbitMQ e PostgreSQL precisam rodar em produção com custo baixo, operados
por uma pessoa.

## Decisão

Docker Compose em uma VPS, com Traefik na borda (TLS Let's Encrypt). Imagens publicadas no GHCR a
cada merge (DEV-0017); deploy por workflow manual via SSH. PostgreSQL gerenciado ou em container com
backup diário para storage externo. Staging só quando houver usuários reais.

## Alternativas consideradas

- **PaaS (Railway, Render, Fly):** deploy por git push, mas o custo cresce rápido com vários
  serviços, broker e bancos.
- **Kubernetes gerenciado:** escala e padrão de mercado, mas complexidade e custo altos para o
  início.

## Consequências

- VPS única é ponto único de falha: aceito no MVP, com backups externos e procedimento de
  restauração testado.
- Como tudo já é container, migrar para Kubernetes depois é direto.
