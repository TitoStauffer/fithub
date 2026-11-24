-- Plano alimentar (DEV-0081, FR-42, FR-43, FR-45): a nutricionista monta para o paciente vinculado, com refeições
-- (nome, horário, ordem, observação) e alimentos com quantidade. Rascunho e publicação, uma ativa por paciente. O
-- conteúdo fica numa versão desde o rascunho, para editar o publicado gerar outra versão sem mexer na anterior
-- (DEV-0083, BR-09), como a ficha de treino (training-service, V8).

create table meal_plan (
    id uuid primary key,
    -- O sub do paciente e o da nutricionista autora (ADR-009).
    client_id uuid not null,
    nutritionist_id uuid not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_meal_plan_status check (status in ('DRAFT', 'PUBLISHED', 'REPLACED'))
);

-- Um plano ativo por paciente (BR-07: uma nutricionista ativa por vez).
create unique index ux_meal_plan_published on meal_plan (client_id) where status = 'PUBLISHED';
create index ix_meal_plan_author on meal_plan (nutritionist_id, client_id);

create table meal_plan_version (
    id uuid primary key,
    plan_id uuid not null references meal_plan (id) on delete cascade,
    number int not null,
    name varchar(80) not null,
    starts_on date not null,
    note varchar(500),
    -- Vazio enquanto a versão é rascunho.
    published_at timestamptz,
    created_at timestamptz not null,
    constraint uq_meal_plan_version unique (plan_id, number),
    constraint ck_meal_plan_version_number check (number >= 1),
    constraint ck_meal_plan_version_name check (char_length(btrim(name)) >= 2)
);

create table meal (
    version_id uuid not null references meal_plan_version (id) on delete cascade,
    -- Ordem da refeição no dia.
    position int not null,
    name varchar(60) not null,
    -- Horário sugerido, opcional.
    suggested_time time,
    note varchar(500),
    primary key (version_id, position),
    constraint ck_meal_position check (position between 0 and 9),
    constraint ck_meal_name check (char_length(btrim(name)) >= 2)
);

create table meal_item (
    version_id uuid not null,
    meal_position int not null,
    position int not null,
    food_id uuid not null references food (id),
    -- Quantidade na unidade base do alimento (g ou ml).
    amount numeric(6, 1) not null,
    -- A medida caseira usada para exibir (ex.: 2 colheres), opcional: a medida e quantas; a quantidade vem dela.
    measure_id uuid references food_measure (id) on delete set null,
    measure_count numeric(4, 1),
    primary key (version_id, meal_position, position),
    foreign key (version_id, meal_position) references meal (version_id, position) on delete cascade,
    constraint ck_meal_item_position check (position between 0 and 29),
    constraint ck_meal_item_amount check (amount between 0.1 and 5000),
    constraint ck_meal_item_measure check (measure_count is null or measure_count between 0.1 and 100)
);

create index ix_meal_item_food on meal_item (food_id);
