# FitHub — Modelagem DDD (MVP)

**Versão:** 0.1.0  
**Data:** 2026-08-12

---

## 1. DOMÍNIO: IDENTITY

### 1.1 Agregados

#### Agregado: User (Raiz)

```
User (AggregateRoot)
├── id: UUID
├── email: Email (Value Object)
├── passwordHash: String
├── role: UserRole (enum: PT | NUTRITIONIST | PATIENT)
├── name: String
├── createdAt: LocalDateTime
├── updatedAt: LocalDateTime
└── status: UserStatus (enum: ACTIVE | INACTIVE)
```

**Responsabilidades**:
- Validar email único
- Criptografar password (bcrypt)
- Controlar status (ativo/inativo)

#### Value Objects

**Email**
```
Email
├── value: String
└── métodos: isValid(), equals()
```

**UserRole**
```
enum UserRole {
  PT,
  NUTRITIONIST,
  PATIENT
}
```

### 1.2 Domain Events

```
UserRegistered
├── userId: UUID
├── email: String
├── role: UserRole
└── createdAt: LocalDateTime

UserAuthenticated
├── userId: UUID
├── role: UserRole
└── authenticatedAt: LocalDateTime
```

### 1.3 Repository Interface

```java
interface UserRepository {
  save(User): void
  findById(UUID): User?
  findByEmail(Email): User?
  findAll(): List<User>
}
```

### 1.4 Domain Service

```java
interface UserRegistrationService {
  registerUser(email, password, role): User
    // Valida email único
    // Criptografa password
    // Emite UserRegistered event
}
```

### 1.5 Use Cases (Application)

**UC-ID-001: Register User**
```
Input: email, password, role
Output: userId, email, role
Fluxo:
  1. Validar email (formato + único)
  2. Validar password (força)
  3. Criar User (UserRegistrationService)
  4. Salvar no banco
  5. Emitir UserRegistered event
  6. Retornar userId
Exceções: EmailAlreadyExists, InvalidEmail, WeakPassword
```

**UC-ID-002: Authenticate User**
```
Input: email, password
Output: token (JWT)
Fluxo:
  1. Buscar User por email
  2. Validar password (bcrypt.compare)
  3. Gerar JWT (exp 7 dias, sub: userId, role: role)
  4. Emitir UserAuthenticated event
  5. Retornar token
Exceções: UserNotFound, InvalidPassword
```

**UC-ID-003: Validate Token**
```
Input: token (JWT)
Output: userId, role
Fluxo:
  1. Decodificar JWT
  2. Validar signature + exp
  3. Retornar (userId, role)
Exceções: InvalidToken, ExpiredToken
```

### 1.6 Estrutura de Banco (PostgreSQL)

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('PT', 'NUTRITIONIST', 'PATIENT')),
  name VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
```

---

## 2. DOMÍNIO: PATIENTS

### 2.1 Agregados

#### Agregado: Patient (Raiz)

```
Patient (AggregateRoot)
├── id: UUID
├── userId: UUID (FK → User)
├── name: String
├── biometrics: Biometrics (Value Object)
│   ├── weight: Double (kg)
│   ├── age: Int (anos)
│   ├── height: Double (cm)
│   └── imc: Double (calculado)
├── restrictions: List<HealthRestriction>
├── createdAt: LocalDateTime
├── updatedAt: LocalDateTime
└── status: PatientStatus (enum: ACTIVE | INACTIVE)
```

**Responsabilidades**:
- Calcular IMC automaticamente: (weight / (height/100)²)
- Validar biometria (weight > 0, age > 0, height > 0)
- Gerenciar restrições de saúde
- Manter histórico de biometria (para gráficos de evolução)

#### Agregado: PatientProfessionalRelationship

```
PatientProfessionalRelationship (AggregateRoot)
├── id: UUID
├── patientId: UUID (FK → Patient)
├── professionalId: UUID (FK → User)
├── professionalRole: ProfessionalRole (PT | NUTRITIONIST)
├── status: RelationshipStatus (PENDING | ACTIVE | INACTIVE)
├── invitationToken: String? (para aceitar convite)
├── acceptedAt: LocalDateTime?
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

**Responsabilidades**:
- Controlar relacionamento entre profissional e paciente
- Validar que não há duplicatas (mesmo PT + mesmo Paciente)
- Gerar token de convite único

#### Entity: HealthRestriction

```
HealthRestriction (Entity, not root)
├── id: UUID
├── patientId: UUID
├── type: RestrictionType (enum)
│   ├── KNEE_SURGERY
│   ├── SHOULDER_INJURY
│   ├── SPINE_ISSUE
│   ├── FOOD_ALLERGY
│   ├── FOOD_INTOLERANCE
│   ├── ...
├── description: String (campo livre, opcional)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

#### Entity: BiometricHistory

```
BiometricHistory (Entity, not root)
├── id: UUID
├── patientId: UUID
├── weight: Double
├── recordedAt: LocalDateTime
```

**Nota**: mantém histórico de peso para gráficos (últimos 30 dias + histórico completo para relatórios)

### 2.2 Value Objects

**Biometrics**
```
Biometrics
├── weight: Double (kg, > 0)
├── age: Int (anos, > 0)
├── height: Double (cm, > 0)
├── imc: Double (calculado, readonly)
└── métodos: imc(), equals()
```

**RestrictionType** (enum)
```
enum RestrictionType {
  // Saúde
  KNEE_SURGERY, SHOULDER_INJURY, SPINE_ISSUE, HIP_SURGERY, 
  ANKLE_INJURY, ELBOW_INJURY, WRIST_INJURY, ...
  
  // Alergia/Intolerância
  LACTOSE_INTOLERANCE, GLUTEN_INTOLERANCE, NUT_ALLERGY, ...
}
```

### 2.3 Domain Events

```
PatientCreated
├── patientId: UUID
├── userId: UUID
├── name: String
├── createdAt: LocalDateTime

BiometricUpdated
├── patientId: UUID
├── weight: Double
├── recordedAt: LocalDateTime

RestrictionAdded
├── patientId: UUID
├── restrictionType: RestrictionType
├── description: String?

PatientProfessionalRelationshipCreated
├── patientId: UUID
├── professionalId: UUID
├── professionalRole: ProfessionalRole
├── invitationToken: String

PatientProfessionalRelationshipAccepted
├── patientId: UUID
├── professionalId: UUID
├── acceptedAt: LocalDateTime
```

### 2.4 Repository Interfaces

```java
interface PatientRepository {
  save(Patient): void
  findById(UUID): Patient?
  findByUserId(UUID): Patient?
  findAll(): List<Patient>
}

interface PatientProfessionalRelationshipRepository {
  save(Relationship): void
  findById(UUID): Relationship?
  findByPatientId(UUID): List<Relationship>
  findByProfessionalId(UUID): List<Relationship>
  findByPatientIdAndProfessionalId(UUID, UUID): Relationship?
  existsByPatientIdAndProfessionalId(UUID, UUID): boolean
}

interface BiometricHistoryRepository {
  save(BiometricHistory): void
  findByPatientIdLast30Days(UUID): List<BiometricHistory>
  findByPatientId(UUID): List<BiometricHistory>
}
```

### 2.5 Domain Service

```java
interface PatientManagementService {
  createPatientByProfessional(
    professionalId, name, weight, age, height
  ): Patient
    // Cria Patient
    // Cria BiometricHistory inicial
    // Emite PatientCreated event
  
  addRestrictionToPatient(
    patientId, restrictionType, description?
  ): void
    // Valida que restriction não existe já
    // Adiciona restrição
    // Emite RestrictionAdded event
  
  updateBiometrics(
    patientId, weight, age?, height?
  ): void
    // Atualiza Patient.biometrics
    // Recalcula IMC
    // Adiciona entrada em BiometricHistory
    // Emite BiometricUpdated event
}

interface PatientProfessionalRelationshipService {
  inviteProfessionalToPatient(
    patientId, professionalId, professionalRole
  ): invitationToken
    // Valida que paciente existe
    // Valida que profissional existe
    // Valida que relação não existe
    // Cria Relationship com status PENDING
    // Gera token único
    // Emite PatientProfessionalRelationshipCreated event
  
  acceptInvitation(
    patientId, professionalId, invitationToken
  ): void
    // Valida token
    // Muda status para ACTIVE
    // Seta acceptedAt
    // Emite PatientProfessionalRelationshipAccepted event
}
```

### 2.6 Use Cases (Application)

**UC-PAT-001: Professional Adds Patient**
```
Input: professionalId, name, weight, age, height, restrictions[]
Output: patientId
Fluxo:
  1. Validar que profissional existe (via UserRepository)
  2. Validar role (PT ou NUTRITIONIST)
  3. Criar Patient (PatientManagementService)
  4. Adicionar restrições (PatientManagementService)
  5. Criar relação PT ↔ Paciente (status ACTIVE)
  6. Emitir eventos
  7. Retornar patientId
Exceções: ProfessionalNotFound, InvalidRole, InvalidBiometrics
```

**UC-PAT-002: Update Patient Biometrics**
```
Input: patientId, weight, age?, height?
Output: patient
Fluxo:
  1. Buscar Patient
  2. Validar que user atual tem acesso (PT ou nutricionista do paciente)
  3. Atualizar biometria (PatientManagementService)
  4. Retornar patient atualizado
Exceções: PatientNotFound, Unauthorized, InvalidBiometrics
```

**UC-PAT-003: List Patients (por profissional)**
```
Input: professionalId
Output: List<Patient>
Fluxo:
  1. Buscar todas as Relationships onde professionalId = current user
  2. Para cada relação, buscar Patient
  3. Retornar com biometria + restrições
Exceções: none (retorna lista vazia se nenhum)
```

### 2.7 Estrutura de Banco (PostgreSQL)

```sql
CREATE TABLE patients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID UNIQUE NOT NULL REFERENCES users(id),
  name VARCHAR(255) NOT NULL,
  weight DECIMAL(5, 2) NOT NULL CHECK (weight > 0),
  age INT NOT NULL CHECK (age > 0),
  height DECIMAL(5, 2) NOT NULL CHECK (height > 0),
  imc DECIMAL(5, 2) GENERATED ALWAYS AS (ROUND(weight / POWER(height/100, 2), 2)) STORED,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE health_restrictions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  type VARCHAR(100) NOT NULL,
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE biometric_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  weight DECIMAL(5, 2) NOT NULL,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE patient_professional_relationships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  professional_role VARCHAR(20) NOT NULL CHECK (professional_role IN ('PT', 'NUTRITIONIST')),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE')),
  invitation_token VARCHAR(255) UNIQUE,
  accepted_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(patient_id, professional_id, professional_role)
);

CREATE INDEX idx_patients_user_id ON patients(user_id);
CREATE INDEX idx_patients_status ON patients(status);
CREATE INDEX idx_health_restrictions_patient_id ON health_restrictions(patient_id);
CREATE INDEX idx_biometric_history_patient_id ON biometric_history(patient_id);
CREATE INDEX idx_biometric_history_recorded_at ON biometric_history(recorded_at);
CREATE INDEX idx_relationships_patient_id ON patient_professional_relationships(patient_id);
CREATE INDEX idx_relationships_professional_id ON patient_professional_relationships(professional_id);
CREATE INDEX idx_relationships_status ON patient_professional_relationships(status);
```

---

## 3. DOMÍNIO: TRAINING

### 3.1 Agregados

#### Agregado: WorkoutPlan (Raiz)

```
WorkoutPlan (AggregateRoot)
├── id: UUID
├── patientId: UUID (FK)
├── ptId: UUID (FK → User.PT)
├── version: Int (incremental, 1, 2, 3, ...)
├── days: Map<DayOfWeek, List<WorkoutExerciseDetails>>
├── isActive: Boolean (última versão é active)
├── createdAt: LocalDateTime
├── activatedAt: LocalDateTime?
├── updatedAt: LocalDateTime
└── status: PlanStatus (DRAFT | ACTIVE | ARCHIVED)
```

**Responsabilidades**:
- Validar que exercícios não violam restrições do paciente (verificar muscleGroups vs restrictions)
- Manter histórico de versões (para auditoria)
- Controlar qual versão está ativa

#### Entity: WorkoutExerciseDetails (não é root)

```
WorkoutExerciseDetails (Entity)
├── id: UUID
├── exerciseId: UUID (FK → Exercise)
├── sets: Int
├── reps: Int
├── weight: Double (kg)
├── alternatives: List<ExerciseAlternative>
├── videoUrl: String (da ExerciseDB)
├── notes: String?
└── order: Int (ordem no dia)
```

#### Entity: ExerciseAlternative

```
ExerciseAlternative
├── id: UUID
├── alternativeExerciseId: UUID (FK → Exercise)
├── reason: String? (ex: "se não tiver barra")
```

#### Agregado: WorkoutExecution (Raiz)

```
WorkoutExecution (AggregateRoot)
├── id: UUID
├── patientId: UUID (FK)
├── workoutPlanVersionId: UUID (qual versão do plano foi feita)
├── exerciseId: UUID (FK → Exercise)
├── dayOfWeek: DayOfWeek
├── actualSets: Int
├── actualReps: Int
├── actualWeight: Double
├── feeling: String (text, ex: "Senti dor no ombro após série 3")
├── executedAt: LocalDateTime
├── createdAt: LocalDateTime (para auditoria, append-only)
└── status: ExecutionStatus (COMPLETED | PARTIAL | SKIPPED)
```

**Responsabilidades**:
- Append-only: nunca edita/deleta, só adiciona novos registros
- Rastrear qual versão do plano foi executada (para saber se plano mudou depois)

#### Value Object: Muscle Group

```
Muscle Group (enum)
- CHEST, BACK, SHOULDER, BICEP, TRICEP, FOREARM,
- QUADRICEP, HAMSTRING, GLUTE, CALF, ABS, OBLIQUE,
- NECK, LOWER_BACK, ...
```

### 3.2 Domain Events

```
WorkoutPlanCreated
├── workoutPlanId: UUID
├── patientId: UUID
├── ptId: UUID
├── version: Int
├── createdAt: LocalDateTime

WorkoutPlanUpdated
├── workoutPlanId: UUID
├── patientId: UUID
├── version: Int (nova versão)
├── updatedAt: LocalDateTime

WorkoutExerciseValidationWarning
├── workoutPlanId: UUID
├── exerciseId: UUID
├── patientRestrictionType: RestrictionType
├── message: String (ex: "Paciente tem lesão no joelho, agachamento pode agravar")

WorkoutExecuted
├── executionId: UUID
├── patientId: UUID
├── exerciseId: UUID
├── actualSets: Int
├── actualReps: Int
├── actualWeight: Double
├── feeling: String
├── executedAt: LocalDateTime
```

### 3.3 Repository Interfaces

```java
interface WorkoutPlanRepository {
  save(WorkoutPlan): void
  findById(UUID): WorkoutPlan?
  findByPatientIdAndLatestVersion(UUID): WorkoutPlan? // Ativa
  findByPatientIdAllVersions(UUID): List<WorkoutPlan>
  findByPtId(UUID): List<WorkoutPlan> // Todas os pacientes do PT
}

interface WorkoutExecutionRepository {
  save(WorkoutExecution): void
  findById(UUID): WorkoutExecution?
  findByPatientIdLast30Days(UUID): List<WorkoutExecution> // Para gráficos
  findByPatientIdAndExerciseId(UUID, UUID): List<WorkoutExecution>
  findByPatientIdOrderByExecutedAtDesc(UUID): List<WorkoutExecution>
}

interface ExerciseRepository {
  save(Exercise): void
  findById(UUID): Exercise?
  findByExternalId(String): Exercise? // From ExerciseDB
  findAllByMuscleGroup(MuscleGroup): List<Exercise>
  findAll(): List<Exercise>
}
```

### 3.4 Domain Service

```java
interface WorkoutPlanService {
  createWorkoutPlan(
    patientId, ptId, daysAndExercises: Map<DayOfWeek, List<ExerciseSpec>>
  ): WorkoutPlan
    // Valida que PT é responsável pelo paciente
    // Para cada exercício: validar contra restrições do paciente
    //   Se violação: emitir WorkoutExerciseValidationWarning (mas não bloqueia)
    // Criar WorkoutPlan(version=1)
    // Emitir WorkoutPlanCreated event
    // Retornar plano
  
  updateWorkoutPlan(
    workoutPlanId, daysAndExercises: Map<DayOfWeek, List<ExerciseSpec>>
  ): WorkoutPlan
    // Mesmo fluxo acima
    // Incrementa version (versão antiga fica em ARCHIVED)
    // Nova versão é ACTIVE
    // Emitir WorkoutPlanUpdated event
}

interface WorkoutExecutionService {
  recordExecution(
    patientId, exerciseId, dayOfWeek, actualSets, actualReps, actualWeight, feeling
  ): WorkoutExecution
    // Buscar WorkoutPlan ativo
    // Validar que exercício está no plano (para aquele dia)
    // Criar WorkoutExecution (append-only)
    // Emitir WorkoutExecuted event
    // Retornar execução
}

interface ExerciseValidationService {
  validateExerciseAgainstRestrictions(
    exerciseId, patientId
  ): ValidationResult
    // Buscar Exercise (muscle groups)
    // Buscar Patient (restrictions)
    // Verificar se algum muscle group usa parte restrita
    // Retornar { isValid, warnings[] }
    // Se não é válido: lançar exception ou returnar warning
}
```

### 3.5 Use Cases (Application)

**UC-TRN-001: PT Creates Workout Plan**
```
Input: patientId, daysAndExercises (map: day → exercises)
Output: workoutPlanId, version, warnings[]
Fluxo:
  1. Validar que PT criador tem acesso a esse paciente
  2. Para cada exercício: validar contra restrições (ExerciseValidationService)
  3. Criar WorkoutPlan(version=1, status=ACTIVE)
  4. Salvar no banco
  5. Emitir WorkoutPlanCreated event
  6. Retornar (workoutPlanId, version=1, warnings[])
Exceções: PatientNotFound, Unauthorized, InvalidExercise
Warnings: "Paciente tem lesão no joelho, este exercício usa quadriceps"
```

**UC-TRN-002: PT Updates Workout Plan**
```
Input: workoutPlanId, daysAndExercises
Output: workoutPlanId, version (incremented)
Fluxo:
  1. Buscar plano anterior (version N)
  2. Validar restrições (como acima)
  3. Criar nova versão (version N+1, status=ACTIVE)
  4. Marcar versão anterior como ARCHIVED
  5. Emitir WorkoutPlanUpdated event
  6. Retornar (workoutPlanId, version=N+1)
Exceções: PlanNotFound, Unauthorized
```

**UC-TRN-003: Patient Records Workout Execution**
```
Input: patientId, exerciseId, dayOfWeek, actualSets, actualReps, actualWeight, feeling
Output: executionId
Fluxo:
  1. Buscar WorkoutPlan ativo do paciente
  2. Validar que exercício está no plano para aquele dia
  3. Criar WorkoutExecution (append-only)
  4. Salvar no banco
  5. Emitir WorkoutExecuted event
  6. Retornar executionId
Exceções: PlanNotFound, ExerciseNotInPlan, Unauthorized
```

**UC-TRN-004: PT Views Patient Progress (last 30 days)**
```
Input: patientId
Output: { exerciseId → { dates: [weight progression] } }
Fluxo:
  1. Buscar todas WorkoutExecutions do paciente (últimos 30 dias)
  2. Agrupar por exerciseId
  3. Montar progression de carga (weight over time)
  4. Retornar estrutura para gráfico
Exceções: PatientNotFound, Unauthorized
```

### 3.6 Estrutura de Banco (PostgreSQL)

```sql
CREATE TABLE exercises (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  external_id VARCHAR(255) UNIQUE, -- ID do ExerciseDB
  name VARCHAR(255) NOT NULL,
  muscle_groups TEXT NOT NULL, -- JSON array: ["CHEST", "TRICEP"]
  equipment VARCHAR(255),
  video_url VARCHAR(512),
  gif_url VARCHAR(512),
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workout_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  pt_id UUID NOT NULL REFERENCES users(id),
  version INT NOT NULL DEFAULT 1,
  is_active BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  activated_at TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(patient_id, version)
);

CREATE TABLE workout_plan_exercises (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_plan_id UUID NOT NULL REFERENCES workout_plans(id) ON DELETE CASCADE,
  exercise_id UUID NOT NULL REFERENCES exercises(id),
  sets INT NOT NULL CHECK (sets > 0),
  reps INT NOT NULL CHECK (reps > 0),
  weight DECIMAL(5, 2) NOT NULL CHECK (weight > 0),
  video_url VARCHAR(512),
  notes TEXT,
  day_of_week VARCHAR(10) NOT NULL,
  order_in_day INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE exercise_alternatives (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_plan_exercise_id UUID NOT NULL REFERENCES workout_plan_exercises(id) ON DELETE CASCADE,
  alternative_exercise_id UUID NOT NULL REFERENCES exercises(id),
  reason TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workout_executions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  workout_plan_id UUID NOT NULL REFERENCES workout_plans(id),
  exercise_id UUID NOT NULL REFERENCES exercises(id),
  day_of_week VARCHAR(10) NOT NULL,
  actual_sets INT NOT NULL CHECK (actual_sets > 0),
  actual_reps INT NOT NULL CHECK (actual_reps > 0),
  actual_weight DECIMAL(5, 2) NOT NULL CHECK (actual_weight > 0),
  feeling TEXT,
  executed_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'PARTIAL', 'SKIPPED')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exercises_name ON exercises(name);
CREATE INDEX idx_exercises_external_id ON exercises(external_id);
CREATE INDEX idx_workout_plans_patient_id ON workout_plans(patient_id);
CREATE INDEX idx_workout_plans_pt_id ON workout_plans(pt_id);
CREATE INDEX idx_workout_plans_active ON workout_plans(patient_id) WHERE is_active = true;
CREATE INDEX idx_workout_executions_patient_id ON workout_executions(patient_id);
CREATE INDEX idx_workout_executions_exercise_id ON workout_executions(exercise_id);
CREATE INDEX idx_workout_executions_executed_at ON workout_executions(executed_at);
```

---

## 4. DOMÍNIO: NUTRITION

### 4.1 Agregados

#### Agregado: DietPlan (Raiz)

```
DietPlan (AggregateRoot)
├── id: UUID
├── patientId: UUID (FK)
├── nutritionistId: UUID (FK → User.NUTRITIONIST)
├── version: Int
├── meals: List<MealSpecification>
├── isActive: Boolean
├── createdAt: LocalDateTime
├── activatedAt: LocalDateTime?
├── updatedAt: LocalDateTime
└── status: PlanStatus (DRAFT | ACTIVE | ARCHIVED)
```

#### Entity: MealSpecification

```
MealSpecification
├── id: UUID
├── name: String (ex: "Café da Manhã", "Pré-treino")
├── order: Int (ordem do dia)
├── foods: List<FoodItem>
└── macros: Macros (calculado)
```

#### Entity: FoodItem

```
FoodItem
├── id: UUID
├── name: String
├── quantity: Double
├── unit: String (g, ml, colher, xícara, etc)
├── macros: Macros (por quantidade)
└── notes: String?
```

#### Agregado: MealExecution (Raiz)

```
MealExecution (AggregateRoot)
├── id: UUID
├── patientId: UUID
├── dietPlanVersionId: UUID
├── mealId: UUID (da qual meal)
├── executedAt: LocalDate
├── completed: Boolean
├── macrosOverride: Macros? (se paciente registrou algo diferente)
├── notes: String? (ex: "Comi a refeição + um bolo")
├── feeling: String? (como está se sentindo)
├── createdAt: LocalDateTime
└── status: ExecutionStatus (COMPLETED | PARTIAL | DEVIATED)
```

**Responsabilidades**:
- Append-only
- Pode ter macros diferentes do prescrito (override)

#### Value Object: Macros

```
Macros
├── protein: Double (g)
├── carbs: Double (g)
├── fat: Double (g)
├── calories: Double (kcal, calculado)
└── métodos: total(), equals()
```

### 4.2 Domain Events

```
DietPlanCreated
├── dietPlanId: UUID
├── patientId: UUID
├── nutritionistId: UUID
├── version: Int

DietPlanUpdated
├── dietPlanId: UUID
├── version: Int (incremented)

MealExecuted
├── executionId: UUID
├── patientId: UUID
├── mealId: UUID
├── completed: Boolean
├── macros: Macros (actual)
├── executedAt: LocalDate

DietDeviationRecorded
├── executionId: UUID
├── patientId: UUID
├── macros: Macros
├── notes: String
├── recordedAt: LocalDateTime
```

### 4.3 Repository Interfaces

```java
interface DietPlanRepository {
  save(DietPlan): void
  findById(UUID): DietPlan?
  findByPatientIdAndLatestVersion(UUID): DietPlan?
  findByPatientIdAllVersions(UUID): List<DietPlan>
}

interface MealExecutionRepository {
  save(MealExecution): void
  findById(UUID): MealExecution?
  findByPatientIdAndDate(UUID, LocalDate): List<MealExecution>
  findByPatientIdLast30Days(UUID): List<MealExecution>
  findByPatientIdOrderByExecutedAtDesc(UUID): List<MealExecution>
}
```

### 4.4 Domain Service

```java
interface DietPlanService {
  createDietPlan(
    patientId, nutritionistId, mealSpecs: List<MealSpec>
  ): DietPlan
    // Valida que nutricionista é responsável pelo paciente
    // Calcula macros para cada refeição
    // Cria DietPlan(version=1)
    // Emitir DietPlanCreated event
  
  updateDietPlan(
    dietPlanId, mealSpecs: List<MealSpec>
  ): DietPlan
    // Incrementa version
    // Marca versão anterior como ARCHIVED
    // Emitir DietPlanUpdated event
}

interface MealExecutionService {
  recordMealExecution(
    patientId, mealId, completed, macrosOverride?, notes?, feeling?
  ): MealExecution
    // Buscar DietPlan ativo
    // Validar que meal está no plano
    // Criar MealExecution
    // Se macrosOverride: usar aquele, senão usar do meal
    // Emitir MealExecuted event (ou DietDeviationRecorded se override)
}
```

### 4.5 Use Cases (Application)

**UC-NUT-001: Nutritionist Creates Diet Plan**
```
Input: patientId, meals[] (name, foods[])
Output: dietPlanId, version
Fluxo:
  1. Validar que nutricionista tem acesso ao paciente
  2. Para cada meal: calcular macros (sum de foods)
  3. Criar DietPlan(version=1)
  4. Emitir DietPlanCreated event
  5. Retornar (dietPlanId, version=1)
Exceções: PatientNotFound, Unauthorized
```

**UC-NUT-002: Nutritionist Updates Diet Plan**
```
Input: dietPlanId, meals[]
Output: dietPlanId, version (incremented)
Fluxo:
  1. Buscar plano anterior
  2. Criar nova versão (N+1)
  3. Marcar anterior como ARCHIVED
  4. Emitir DietPlanUpdated event
Exceções: PlanNotFound, Unauthorized
```

**UC-NUT-003: Patient Records Meal Execution**
```
Input: patientId, mealId, date, completed, macrosOverride?, notes?, feeling?
Output: executionId
Fluxo:
  1. Buscar DietPlan ativo
  2. Validar que meal está no plano
  3. Criar MealExecution
  4. Se macrosOverride: status=DEVIATED, senão status=COMPLETED/PARTIAL
  5. Emitir MealExecuted ou DietDeviationRecorded event
Exceções: PlanNotFound, MealNotInPlan, Unauthorized
```

**UC-NUT-004: Nutritionist Views Patient Progress (last 30 days)**
```
Input: patientId
Output: { date → { mealId → { completed, macros } }, dailyMacros, dailyAdherence }
Fluxo:
  1. Buscar MealExecutions (últimos 30 dias)
  2. Agrupar por data
  3. Para cada data: calcular total de macros
  4. Calcular taxa de aderência (% meals registrados)
  5. Retornar estrutura para gráfico
Exceções: PatientNotFound, Unauthorized
```

### 4.6 Estrutura de Banco (PostgreSQL)

```sql
CREATE TABLE diet_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  nutritionist_id UUID NOT NULL REFERENCES users(id),
  version INT NOT NULL DEFAULT 1,
  is_active BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  activated_at TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(patient_id, version)
);

CREATE TABLE meals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  diet_plan_id UUID NOT NULL REFERENCES diet_plans(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  order_in_day INT,
  protein DECIMAL(6, 2) NOT NULL DEFAULT 0,
  carbs DECIMAL(6, 2) NOT NULL DEFAULT 0,
  fat DECIMAL(6, 2) NOT NULL DEFAULT 0,
  calories DECIMAL(8, 2) GENERATED ALWAYS AS (ROUND(protein * 4 + carbs * 4 + fat * 9, 2)) STORED,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE foods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  quantity DECIMAL(8, 2) NOT NULL,
  unit VARCHAR(50) NOT NULL,
  protein DECIMAL(6, 2) NOT NULL,
  carbs DECIMAL(6, 2) NOT NULL,
  fat DECIMAL(6, 2) NOT NULL,
  calories DECIMAL(8, 2) GENERATED ALWAYS AS (ROUND(protein * 4 + carbs * 4 + fat * 9, 2)) STORED,
  notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE meal_executions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  diet_plan_id UUID NOT NULL REFERENCES diet_plans(id),
  meal_id UUID NOT NULL REFERENCES meals(id),
  executed_at DATE NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  protein_override DECIMAL(6, 2),
  carbs_override DECIMAL(6, 2),
  fat_override DECIMAL(6, 2),
  notes TEXT,
  feeling TEXT,
  status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'PARTIAL', 'DEVIATED')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diet_plans_patient_id ON diet_plans(patient_id);
CREATE INDEX idx_diet_plans_active ON diet_plans(patient_id) WHERE is_active = true;
CREATE INDEX idx_meals_diet_plan_id ON meals(diet_plan_id);
CREATE INDEX idx_foods_meal_id ON foods(meal_id);
CREATE INDEX idx_meal_executions_patient_id ON meal_executions(patient_id);
CREATE INDEX idx_meal_executions_date ON meal_executions(executed_at);
```

---

## 5. Relacionamentos Entre Domínios

```
Identity
  ↓
Patients ← (User.PT | User.NUTRITIONIST)
  ↓
Training ← (Exercise do Catalog)
Nutrition

Relacionamentos:
- Patient.userId → User.id (1:1)
- WorkoutPlan.ptId → User.id (N:1)
- WorkoutPlan.patientId → Patient.id (N:1)
- DietPlan.nutritionistId → User.id (N:1)
- DietPlan.patientId → Patient.id (N:1)
- WorkoutExecution.patientId → Patient.id (N:1)
- MealExecution.patientId → Patient.id (N:1)
- PatientProfessionalRelationship.patientId → Patient.id
- PatientProfessionalRelationship.professionalId → User.id
```

---

## 6. Fluxo de Implementação Sugerido

1. **Fase 1: Identity** (1-2 dias)
   - Agregado User, autenticação, JWT
   - Endpoints: POST /auth/register, POST /auth/login, GET /auth/validate

2. **Fase 2: Patients** (2-3 dias)
   - Agregados Patient, PatientProfessionalRelationship, BiometricHistory
   - Endpoints: POST /patients (criar), PUT /patients/{id}/biometrics, GET /patients (listar do prof.)
   - Importância: crítico, sem pacientes, nada funciona

3. **Fase 3: Training** (3-4 dias)
   - Agregados WorkoutPlan, WorkoutExecution, Exercise (cache ExerciseDB)
   - Endpoints: POST /workout-plans, PUT /workout-plans/{id}, POST /workout-executions, GET /progress
   - Importância: valor pra PT

4. **Fase 4: Nutrition** (2-3 dias)
   - Agregados DietPlan, MealExecution
   - Endpoints: POST /diet-plans, PUT /diet-plans/{id}, POST /meal-executions, GET /progress
   - Importância: valor pra nutricionista, padrão similar a Training

---

**Próximo passo**: revisar esta modelagem (agregados, events, use cases, banco) antes de começar o código.

Tá ok ou tem ajustes?
