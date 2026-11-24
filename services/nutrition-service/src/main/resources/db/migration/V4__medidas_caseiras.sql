-- Medidas caseiras dos alimentos (DEV-0076, FR-37): colher, unidade, fatia… e quanto valem na unidade base do
-- alimento. Cada medida é da nutricionista que cadastrou e só ela vê (BR-13); a TACO não traz medidas caseiras.

create table food_measure (
    id uuid primary key,
    food_id uuid not null references food (id) on delete cascade,
    -- O sub da nutricionista (ADR-009).
    owner_id uuid not null,
    name varchar(40) not null,
    -- Quanto uma medida vale na unidade base do alimento (g ou ml).
    amount numeric(5, 1) not null,
    created_at timestamptz not null,
    constraint ck_food_measure_name check (char_length(btrim(name)) >= 2),
    constraint ck_food_measure_amount check (amount between 0.1 and 2000)
);

-- Sem nome repetido (sem diferenciar maiúsculas) nas medidas da mesma nutricionista no mesmo alimento.
create unique index ux_food_measure_name on food_measure (food_id, owner_id, lower(name));
