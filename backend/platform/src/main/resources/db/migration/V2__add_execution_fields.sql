-- Migration V2: Adicionar campos para execução simplificada de treinos e refeições

-- Adicionar coluna exercise_name em workout_executions para registros simples
ALTER TABLE workout_executions ADD COLUMN IF NOT EXISTS exercise_name VARCHAR(255);

-- Adicionar coluna recorded_at (TIMESTAMP) para registros com hora
ALTER TABLE workout_executions ADD COLUMN IF NOT EXISTS recorded_at TIMESTAMP;

-- Adicionar coluna calories em meal_executions para nutrição
ALTER TABLE meal_executions ADD COLUMN IF NOT EXISTS calories INT;

-- Alter meal_executions recorded_at se não existir
ALTER TABLE meal_executions ADD COLUMN IF NOT EXISTS recorded_at TIMESTAMP;

-- Adicionar campos para versionamento e dados JSON
ALTER TABLE workout_plans ADD COLUMN IF NOT EXISTS exercises_json TEXT DEFAULT '{}';
ALTER TABLE diet_plans ADD COLUMN IF NOT EXISTS meals_json TEXT DEFAULT '{}';

-- Adicionar campos de macro targets na diet_plans se não existir
ALTER TABLE diet_plans ADD COLUMN IF NOT EXISTS daily_calorie_target INT;
ALTER TABLE diet_plans ADD COLUMN IF NOT EXISTS daily_protein_target INT;
ALTER TABLE diet_plans ADD COLUMN IF NOT EXISTS daily_carbs_target INT;
ALTER TABLE diet_plans ADD COLUMN IF NOT EXISTS daily_fat_target INT;

-- Criar índices para melhor performance
CREATE INDEX IF NOT EXISTS idx_workout_executions_recorded_at ON workout_executions(recorded_at);
CREATE INDEX IF NOT EXISTS idx_meal_executions_recorded_at ON meal_executions(recorded_at);
