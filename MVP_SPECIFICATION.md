# FitHub MVP — Especificação Executiva

**Versão:** 0.4.0 (MVP)  
**Data:** 2026-08-12  
**Status:** Aprovado

---

## 1. Visão Geral do Produto

**FitHub** é uma plataforma que permite profissionais da saúde e fitness (Personal Trainers, Nutricionistas) orientarem e acompanharem o progresso de seus pacientes em um ecossistema integrado.

### Público-alvo MVP
- **Personal Trainers** (PT): prescrever e acompanhar fichas de treino
- **Nutricionistas**: prescrever e acompanhar planos alimentares
- **Pacientes**: registrar execução de treino/dieta, rastrear progresso, consultar vídeos de exercícios

### Modelo de Monetização
- **SaaS do Profissional**: PT/nutricionista paga mensalidade para gerenciar pacientes
- **App Gratuito do Paciente**: acesso livre, sem cobrança direta
- **Plataformas**: Sistema web para profissionais (desktop), app mobile para pacientes (iOS/Android)

---

## 2. Requisitos Funcionais por Perfil

### 2.1 Personal Trainer

#### Cadastro e Gestão de Pacientes
- [ ] Cadastro de paciente com dados biométricos obrigatórios: nome, peso, idade, altura (IMC calculado automaticamente)
- [ ] Registro de restrições de saúde: dropdown padronizado (Joelho, Ombro, Coluna, Lesão, Cirurgia, etc) + campo de texto livre opcional
- [ ] Editar dados biométricos e restrições a qualquer momento

#### Prescrição de Treino
- [ ] Criar ficha de treino (semana):
  - Prescrição por dia (seg, ter, qua, qui, sex, sab, dom)
  - Por cada exercício: nome (via ExerciseDB), séries, repetições, carga, alternativas, notas
  - Sugestão de vídeo (da API) para cada exercício
  - **Validação crítica**: alertar PT se estiver prescrevendo exercício que usa a parte do corpo restrita do paciente
- [ ] Editar/reatualizar ficha de treino (gera nova versão, histórico preservado)
- [ ] Acompanhar execução do paciente nos últimos 30 dias: gráfico de carga (evolução), taxa de aderência (% treinos completados)

### 2.2 Nutricionista

#### Cadastro e Gestão de Pacientes
- [ ] Mesmo fluxo do PT: biometria + restrições (alérgico a, intolerância, etc)

#### Prescrição de Dieta
- [ ] Criar plano de dieta customizável:
  - Nutricionista define nomes e quantidade de refeições (ex: pré-treino, café, almoço, lanche, janta)
  - Para cada refeição: lista de alimentos com quantidades e macros (proteína, carboidrato, gordura, calorias)
- [ ] Editar/reatualizar plano (nova versão, histórico preservado)
- [ ] Acompanhar aderência do paciente: últimos 30 dias com gráfico de macros/dia, taxa de aderência (% refeições registradas)

### 2.3 Paciente

#### Treino (se contratou o serviço com PT)
- [ ] Visualizar ficha de treino prescrita (semana vigente)
- [ ] Para cada exercício: assistir vídeo (via ExerciseDB)
- [ ] Registrar execução: qual exercício fez, quantas séries/reps, qual carga usou, como se sentia (escala ou texto)
- [ ] Visualizar histórico de execução (últimos 30 dias) com gráfico de carga
- [ ] Dashboard: peso (últimos 30 dias) + taxa de aderência de treino

#### Nutrição (se contratou o serviço com nutricionista)
- [ ] Visualizar plano de dieta prescrito (refeições do dia)
- [ ] Registrar refeição: marcar como feita/não feita, visualizar macros daquela refeição
- [ ] Registrar desvios: "fiz algo diferente" (texto livre + macros manuais se quiser)
- [ ] Registrar sensação do dia: "como está se sentindo" (texto ou escala)
- [ ] Dashboard: tabela de macros por refeição, tabela geral do dia (proteína total, carbs, gordura, calorias), gráfico de macros (últimos 30 dias), peso (últimos 30 dias)

---

## 3. Domínios de Negócio (MVP)

### 3.1 Identity
**Responsabilidade**: Cadastro, autenticação, autorização de usuários  
**Agregados principais**:
- `User` (name, email, password, role: PT | Nutricionista | Paciente)

**Use cases**:
- UC-ID-001: Registrar novo usuário
- UC-ID-002: Autenticar usuário (login)
- UC-ID-003: Validar token / autorizar ação por role

### 3.2 Patients
**Responsabilidade**: Gestão de dados biométricos, restrições e relacionamento profissional↔paciente  
**Agregados principais**:
- `Patient` (name, weight, age, height, imc_calculated, restrictions: List<Restriction>)
- `Restriction` (type: enum, description: text)
- `PatientProfessionalRelationship` (patient_id, professional_id, professional_role: PT | Nutricionista, status: active)

**Use cases**:
- UC-PAT-001: PT/nutricionista cadastra novo paciente
- UC-PAT-002: Atualizar dados biométricos do paciente
- UC-PAT-003: Registrar/editar restrições de saúde
- UC-PAT-004: Listar pacientes do profissional

### 3.3 Training
**Responsabilidade**: Prescrição e acompanhamento de treinos  
**Agregados principais**:
- `WorkoutPlan` (patient_id, pt_id, days: Map<DayOfWeek, List<WorkoutExercise>>, version, created_at, updated_at)
- `WorkoutExercise` (exercise_id, sets, reps, weight, alternatives: List<Alternative>, video_url, notes)
- `WorkoutExecution` (patient_id, exercise_id, date, actual_sets, actual_reps, actual_weight, feeling: text, append_only)
- `Exercise` (external_id from ExerciseDB, name, muscle_groups: List<String>, video_url, gif_url, description)

**Comportamentos**:
- Ao prescrever exercício: validar se algum muscle_group está na lista de restrições do paciente → alertar PT

**Use cases**:
- UC-TRN-001: PT prescreve ficha de treino (create)
- UC-TRN-002: PT atualiza ficha de treino (new version)
- UC-TRN-003: Paciente registra execução de exercício (append-only)
- UC-TRN-004: PT visualiza evolução de carga do paciente (últimos 30 dias)
- UC-TRN-005: Paciente visualiza ficha e vídeos de exercícios

### 3.4 Nutrition
**Responsabilidade**: Prescrição e acompanhamento de planos alimentares  
**Agregados principais**:
- `DietPlan` (patient_id, nutritionist_id, meals: List<Meal>, version, created_at, updated_at)
- `Meal` (name: string, foods: List<Food>, macros: {protein, carbs, fat, calories}, order: int)
- `Food` (name, quantity, unit, protein, carbs, fat, calories)
- `MealExecution` (patient_id, meal_id, date, completed: bool, macros_override: optional, notes: text, append_only)

**Comportamentos**:
- Cálculo de macros da refeição: soma de macros dos alimentos
- Cálculo de macros do dia: soma de todas as refeições executadas

**Use cases**:
- UC-NUT-001: Nutricionista prescreve plano de dieta (create)
- UC-NUT-002: Nutricionista atualiza plano de dieta (new version)
- UC-NUT-003: Paciente registra execução de refeição (append-only)
- UC-NUT-004: Paciente registra desvio / refeição extra (append-only)
- UC-NUT-005: Nutricionista visualiza aderência e macros do paciente (últimos 30 dias)
- UC-NUT-006: Paciente visualiza macros do dia e histórico

---

## 4. Integração com ExerciseDB

**Serviço externo**: ExerciseDB API (11k+ exercícios, vídeos, GIFs)

**Estratégia**:
1. Sincronização inicial: importar catálogo de exercícios via ExerciseDB
2. Armazenar localmente em tabela `exercise` (cache permanente)
3. Atualizar cache mensalmente ou sob demanda

**Dados por exercício**:
- ID, nome, grupos musculares (primary, secondary), equipamento
- Vídeo URL (hd, sd), GIF URL
- Instruções (steps)

---

## 5. Histórico e Gráficos

### Período de Rastreamento
- **Últimos 30 dias** (rolling window)

### Gráficos por Perfil

#### Personal Trainer (visualiza paciente)
- Evolução de carga (por exercício): gráfico de linha (última 30 dias)
- Taxa de aderência: % treinos completados vs prescritos

#### Nutricionista (visualiza paciente)
- Evolução de macros: gráfico (últimos 30 dias) — total de carbs, proteína, gordura/dia
- Taxa de aderência: % refeições registradas vs prescritas

#### Paciente
- Peso: gráfico de linha (últimos 30 dias)
- Macros do dia: tabela (refeição por refeição) + tabela geral
- Evolução de macros: gráfico (últimos 30 dias)
- Evolução de carga (se PT): gráfico (últimos 30 dias)
- Taxa de aderência: treino + nutrição (se aplicável)

---

## 6. Permissões e Segurança

### Matriz de Acesso

| Ação | PT | Nutricionista | Paciente | Admin |
|------|----|----|----------|-------|
| Ver dados próprios | ✅ | ✅ | ✅ | ✅ |
| Ver dados de paciente próprio | ✅ | ✅ | - | ✅ |
| Criar/editar treino do paciente | ✅ | - | - | ✅ |
| Criar/editar dieta do paciente | - | ✅ | - | ✅ |
| Registrar execução de treino | - | - | ✅ | ✅ |
| Registrar execução de refeição | - | - | ✅ | ✅ |
| Ver progresso de paciente (gráficos) | ✅ | ✅ | ✅ | ✅ |

### Comportamentos
- **Append-only**: execuções de treino e refeição nunca são deletadas/editadas, apenas novos registros são adicionados
- **Validações de domínio**: PT não consegue prescrever exercício que usa parte restrita do paciente

---

## 7. Notificações (MVP)

- [ ] Lembretes ao paciente: "Você tem treino hoje" (push notification, time configurável)
- [ ] Lembretes ao paciente: "Registre suas refeições" (push notification, time configurável)
- [ ] Alerta ao PT: "Paciente {nome} não treinou essa semana" (push notification ou dashboard flag)
- [ ] Alerta ao nutricionista: "Paciente {nome} não registrou refeições esse dia" (push notification ou dashboard flag)

---

## 8. Stack Técnico

**Backend**: Java 21, Spring Boot 3.3.4, Maven, monorepo modular  
**Banco**: PostgreSQL 17 (Flyway migrations)  
**Cache**: Redis (para catálogo de exercícios, opcional para MVP)  
**Autenticação**: JWT (básico, sem 2FA no MVP)  
**Frontend**: React (web, para profissionais)  
**Mobile**: React Native (app, para pacientes)  
**API Externa**: ExerciseDB (catálogo de exercícios)

---

## 9. Arquitetura de Domínios (DDD)

Cada domínio é um módulo Maven independente:

```
backend/
├── shared-kernel/          # Tipos base (Aggregate, Value Object, Domain Event)
├── platform/               # Infraestrutura técnica (JPA, Flyway)
├── modules/
│   ├── identity/           # Autenticação e autorização
│   ├── patients/           # Gestão de pacientes e biometria
│   ├── training/           # Prescrição e acompanhamento de treino
│   ├── nutrition/          # Prescrição e acompanhamento de dieta
│   └── catalog/            # Catálogo de exercícios (cache de ExerciseDB)
└── boot/                   # Ponto de entrada (Spring Boot)
```

Cada módulo segue: **Interfaces → Application → Domain → Infrastructure (Hexagonal + Clean Architecture)**

---

## 10. Roadmap Pós-MVP

- [ ] **v1.1**: Dashboard analytics para profissionais (relatórios, tendências)
- [ ] **v1.2**: Importação de foto para progresso visual
- [ ] **v1.3**: Integração com wearables (Fitbit, Apple Watch, etc)
- [ ] **v1.4**: Marketplace de exercícios customizados (PT vende ficha pronta)
- [ ] **v2.0**: IA para sugerir ajustes automáticos (baseado em histórico)
- [ ] **v2.0**: Multi-idioma
- [ ] **v2.1**: Modo offline do app (sincronizar depois)
- [ ] **v2.2**: Feature flags (A/B testing)

---

## 11. Próximos Passos de Implementação

### Ordem Recomendada
1. **Domínio Identity** — autenticação/autorização (base para tudo)
2. **Domínio Patients** — cadastro e biometria
3. **Domínio Training** — prescrição e execução (mais complexo)
4. **Domínio Nutrition** — prescrição e execução (padrão similar a Training)
5. **Domínio Catalog** — sincronização e cache de ExerciseDB

### Cada Domínio Segue
- Modelagem de agregados (TDD com testes de comportamento)
- Migrations (Flyway)
- Endpoints REST (com validações, tratamento de erro)
- Testes de integração (API + banco)

---

**Aprovado por:** Tito Stauffer  
**Data de aprovação:** 2026-08-12
