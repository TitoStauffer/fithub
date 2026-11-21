# PROJECT OVERVIEW

Version: 0.1

## Objetivo

Consolidar as decisões arquiteturais e de produto tomadas durante a definição do FitHub.
Este documento representa a base oficial de conhecimento do projeto.

## Visão do Produto

O FitHub é uma plataforma para gestão de profissionais da saúde e fitness, permitindo o acompanhamento de pacientes, treinos, nutrição, evolução e metas em um único ecossistema.

## Público-alvo (MVP)

- Personal Trainers
- Nutricionistas
- Profissionais independentes

## Evolução futura

- Clínicas
- Academias
- Equipes multidisciplinares

## Aplicações

- Backend único
- Aplicação Web (React)
- Aplicação Mobile (React Native)

## Papéis

- Administrador
- Profissional
- Paciente

Controle de acesso baseado em Roles.

## Stack definida

### Backend
- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- PostgreSQL
- Flyway

### Frontend
- React

### Mobile
- React Native

## Arquitetura

Decisões aprovadas:

- Monorepo
- Modular Monolith
- Domain-Driven Design
- Clean Architecture
- Arquitetura Hexagonal
- CQRS Light

## Autenticação

Aprovado para o MVP:

- Login por e-mail e senha
- Login com Google (OAuth)

## Idiomas

MVP:
- Português (pt-BR)

Futuro:
- Internacionalização

## Domínios

- Identity
- Catalog
- Training
- Nutrition
- Health
- Goals
- Progress

## Funcionalidades aprovadas para o MVP

- Cadastro e autenticação
- Gestão de pacientes
- Catálogo de exercícios
- Prescrição de treinos
- Prescrição de planos alimentares
- Registro da execução do treino
- Registro da evolução do paciente

## Funcionalidades adiadas

- Inteligência Artificial
- CDN própria para mídia
- Wearables
- Marketplace
- Offline
- Feature Flags
- Multi-idioma

## Princípios de Engenharia

- Documentação antes da implementação.
- Pequenos incrementos.
- Projeto sempre compilando.
- Arquitetura protegida por testes.
- Modularização desde o início.
- O Playbook e a Knowledge Base são a fonte oficial da verdade.

## Decisões consolidadas

KB-0001 — Backend em Java + Spring Boot.
KB-0002 — Maven como ferramenta de build.
KB-0003 — Spring Data JPA para persistência.
KB-0004 — React para Web.
KB-0005 — React Native para Mobile.
KB-0006 — Monorepo.
KB-0007 — Monólito Modular.
KB-0008 — Login por e-mail e Google.
KB-0009 — Domínios separados.
KB-0010 — IA e recursos avançados ficam fora do MVP.

## Próximos documentos

- TIMELINE.md
- MVP_DECISIONS.md
- ROADMAP.md
- FUTURE_IDEAS.md
- OPEN_QUESTIONS.md
