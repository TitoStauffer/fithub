-- Séries registradas pelo aluno (DEV-0066, FR-48): carga e repetições reais de cada série de um item da divisão,
-- ligadas à sessão, que guarda a versão da ficha (BR-09). São do aluno (BR-01).

create table workout_set (
    -- Gerado pelo app: registrar de novo com o mesmo id corrige a série em vez de criar outra.
    id uuid primary key,
    session_id uuid not null references workout_session (id),
    item_position int not null,
    set_number int not null,
    -- O exercício feito: o principal do item ou uma alternativa prevista (DEV-0067).
    exercise_id uuid not null references exercise (id),
    load_kg numeric(6, 2) not null,
    reps int not null,
    recorded_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_workout_set_slot unique (session_id, item_position, set_number),
    constraint ck_workout_set_item check (item_position between 0 and 29),
    constraint ck_workout_set_number check (set_number between 1 and 20),
    constraint ck_workout_set_load check (load_kg between 0 and 1000 and load_kg * 4 = trunc(load_kg * 4)),
    constraint ck_workout_set_reps check (reps between 0 and 300)
);

-- Pré-preenchimento: as séries do último treino com o exercício.
create index ix_workout_set_exercise on workout_set (exercise_id, session_id);
