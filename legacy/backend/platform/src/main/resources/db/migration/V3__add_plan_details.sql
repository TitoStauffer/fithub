-- V3: Adicionar colunas faltantes em workout_plans e diet_plans

-- Adicionar coluna exercises_json em workout_plans
ALTER TABLE workout_plans
ADD COLUMN IF NOT EXISTS exercises_json TEXT DEFAULT '{}';

-- Adicionar colunas de macro targets e meals_json em diet_plans
ALTER TABLE diet_plans
ADD COLUMN IF NOT EXISTS daily_calorie_target INT,
ADD COLUMN IF NOT EXISTS daily_protein_target INT,
ADD COLUMN IF NOT EXISTS daily_carbs_target INT,
ADD COLUMN IF NOT EXISTS daily_fat_target INT,
ADD COLUMN IF NOT EXISTS meals_json TEXT DEFAULT '{}';
