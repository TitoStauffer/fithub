-- Registro de refeição pelo paciente (DEV-0087, FR-50): cada refeição do plano publicado, por dia, como feita, parcial
-- ou não feita, com a alternativa usada em cada alimento. O registro fica na versão do plano em que foi feito (BR-09) e
-- é do paciente (BR-01). O id vem do app: repetir o envio corrige o mesmo registro.

create table meal_log (
    id uuid primary key,
    -- O sub do paciente (ADR-009).
    client_id uuid not null,
    plan_id uuid not null references meal_plan (id),
    version_id uuid not null references meal_plan_version (id),
    version_number int not null,
    meal_position int not null,
    -- O dia em America/Sao_Paulo; a correção vale só nele (BR-08).
    logged_on date not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    -- Uma refeição do plano por dia.
    constraint uq_meal_log_slot unique (client_id, plan_id, meal_position, logged_on),
    constraint ck_meal_log_status check (status in ('DONE', 'PARTIAL', 'SKIPPED')),
    constraint ck_meal_log_meal_position check (meal_position between 0 and 9)
);

create index ix_meal_log_plan_day on meal_log (plan_id, logged_on);

-- A alternativa usada num alimento da refeição; o alimento sem linha aqui foi o principal.
create table meal_log_substitution (
    log_id uuid not null references meal_log (id) on delete cascade,
    item_position int not null,
    -- 0 é a secundária, 1 a terciária.
    alternative int not null,
    primary key (log_id, item_position),
    constraint ck_meal_log_substitution_item check (item_position between 0 and 29),
    constraint ck_meal_log_substitution_alternative check (alternative between 0 and 1)
);
