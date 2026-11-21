-- ========== FITHUB - BASELINE SCHEMA (V1) ==========
-- Versão: 1.0
-- Data: 2026-08-12
-- Descrição: Schema inicial para FitHub com 4 domínios DDD
-- Tipo de dados: PostgreSQL 17

-- ========== DOMÍNIO: IDENTITY ==========

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('PT', 'NUTRITIONIST', 'PATIENT')),
  name VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- ========== DOMÍNIO: PATIENTS ==========

CREATE TABLE patients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  weight FLOAT NOT NULL CHECK (weight > 0),
  age INTEGER NOT NULL CHECK (age > 0),
  height FLOAT NOT NULL CHECK (height > 0),
  imc FLOAT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE biometric_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  weight FLOAT NOT NULL,
  recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE patient_professional_relationships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  professional_role VARCHAR(20) NOT NULL CHECK (professional_role IN ('PT', 'NUTRITIONIST')),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE')),
  invitation_token VARCHAR(255) UNIQUE,
  accepted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(patient_id, professional_id, professional_role)
);

CREATE INDEX idx_patients_user_id ON patients(user_id);
CREATE INDEX idx_patients_status ON patients(status);
CREATE INDEX idx_biometric_history_patient_id ON biometric_history(patient_id);
CREATE INDEX idx_biometric_history_recorded_at ON biometric_history(recorded_at);
CREATE INDEX idx_relationships_patient_id ON patient_professional_relationships(patient_id);
CREATE INDEX idx_relationships_professional_id ON patient_professional_relationships(professional_id);
CREATE INDEX idx_relationships_status ON patient_professional_relationships(status);

-- ========== DOMÍNIO: TRAINING ==========

CREATE TABLE workout_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  pt_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  version INTEGER NOT NULL DEFAULT 1,
  status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  is_active BOOLEAN NOT NULL DEFAULT false,
  exercises_json TEXT DEFAULT '{}',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  activated_at TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(patient_id, version)
);

CREATE TABLE workout_executions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  exercise_name VARCHAR(255) NOT NULL,
  sets INTEGER NOT NULL,
  reps INTEGER NOT NULL,
  weight FLOAT NOT NULL,
  feeling VARCHAR(50),
  status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
  recorded_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_workout_plans_patient_id ON workout_plans(patient_id);
CREATE INDEX idx_workout_plans_pt_id ON workout_plans(pt_id);
CREATE INDEX idx_workout_executions_patient_id ON workout_executions(patient_id);
CREATE INDEX idx_workout_executions_recorded_at ON workout_executions(recorded_at);

-- ========== DOMÍNIO: NUTRITION ==========

CREATE TABLE diet_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  nutritionist_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  version INTEGER NOT NULL DEFAULT 1,
  status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  is_active BOOLEAN NOT NULL DEFAULT false,
  daily_calorie_target INTEGER,
  daily_protein_target INTEGER,
  daily_carbs_target INTEGER,
  daily_fat_target INTEGER,
  meals_json TEXT DEFAULT '{}',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  activated_at TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(patient_id, version)
);

CREATE TABLE meal_executions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  meal_name VARCHAR(255) NOT NULL,
  protein FLOAT NOT NULL DEFAULT 0,
  carbs FLOAT NOT NULL DEFAULT 0,
  fat FLOAT NOT NULL DEFAULT 0,
  calories INTEGER,
  completed BOOLEAN NOT NULL DEFAULT false,
  recorded_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diet_plans_patient_id ON diet_plans(patient_id);
CREATE INDEX idx_diet_plans_nutritionist_id ON diet_plans(nutritionist_id);
CREATE INDEX idx_meal_executions_patient_id ON meal_executions(patient_id);
CREATE INDEX idx_meal_executions_recorded_at ON meal_executions(recorded_at);
