-- Ficha de treino do aluno (DEV-0059, FR-28, FR-29, FR-32, FR-34): N divisões em sequência, início e validade,
-- rascunho e publicação. O conteúdo fica numa versão desde o rascunho, para editar a ficha publicada gerar outra
-- versão sem mexer na anterior (DEV-0061, BR-09).

create table workout_plan (
    id uuid primary key,
    -- O sub do aluno e o do personal autor (ADR-009).
    client_id uuid not null,
    personal_id uuid not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_workout_plan_status check (status in ('DRAFT', 'PUBLISHED', 'REPLACED'))
);

-- Uma ficha ativa por aluno vinda do personal (FR-34).
create unique index ux_workout_plan_published on workout_plan (client_id, personal_id) where status = 'PUBLISHED';
create index ix_workout_plan_personal on workout_plan (personal_id, status);

create table workout_plan_version (
    id uuid primary key,
    plan_id uuid not null references workout_plan (id) on delete cascade,
    number int not null,
    name varchar(80) not null,
    starts_on date not null,
    validity_weeks int not null,
    -- Vazio enquanto a versão é rascunho.
    published_at timestamptz,
    created_at timestamptz not null,
    constraint uq_workout_plan_version unique (plan_id, number),
    constraint ck_workout_plan_version_number check (number >= 1),
    constraint ck_workout_plan_version_name check (char_length(btrim(name)) >= 2),
    constraint ck_workout_plan_version_validity check (validity_weeks between 1 and 52)
);

create table workout_division (
    version_id uuid not null references workout_plan_version (id) on delete cascade,
    -- Ordem na sequência (A, B, C...): a letra é sempre a da posição.
    position int not null,
    letter char(1) not null,
    name varchar(60),
    primary key (version_id, position),
    constraint ck_workout_division_position check (position between 0 and 6),
    constraint ck_workout_division_letter check (letter = chr(65 + position))
);
