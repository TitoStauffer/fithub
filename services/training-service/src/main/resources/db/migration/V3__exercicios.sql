-- Exercícios (DEV-0053, FR-22): catálogo comum e exercícios próprios do personal (BR-13).

create table exercise (
    id uuid primary key,
    kind varchar(16) not null,
    -- O sub do personal dono (ADR-009); vazio no catálogo.
    owner_id uuid,
    name varchar(120) not null,
    equipment varchar(24) not null,
    instructions varchar(4000),
    -- Vídeo ou imagem por URL https; o FitHub não hospeda o arquivo.
    media_url varchar(500),
    -- Origem e licença do item importado (ADR-010): ou os quatro campos, ou nenhum.
    external_source varchar(200),
    external_id varchar(200),
    license varchar(200),
    license_author varchar(200),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_exercise_kind check (kind in ('CATALOG', 'OWN')),
    constraint ck_exercise_owner check ((kind = 'OWN') = (owner_id is not null)),
    constraint ck_exercise_name check (char_length(btrim(name)) >= 2),
    constraint ck_exercise_equipment check (
        equipment in ('BARBELL', 'DUMBBELL', 'KETTLEBELL', 'MACHINE', 'CABLE', 'BAND', 'BODYWEIGHT', 'OTHER')),
    constraint ck_exercise_media_https check (media_url is null or media_url like 'https://%'),
    constraint ck_exercise_attribution check (
        (external_source is null and external_id is null and license is null and license_author is null)
        or (external_source is not null and external_id is not null and license is not null
            and license_author is not null)),
    -- A importação atualiza pelo id externo, sem duplicar (DEV-0054).
    constraint uq_exercise_external unique (external_source, external_id)
);

-- Exercícios próprios de um personal (DEV-0056).
create index ix_exercise_owner on exercise (owner_id) where owner_id is not null;

create table exercise_muscle_group (
    exercise_id uuid not null references exercise (id) on delete cascade,
    muscle_group varchar(24) not null,
    primary key (exercise_id, muscle_group),
    constraint ck_exercise_muscle_group check (
        muscle_group in (
            'CHEST', 'BACK', 'LOWER_BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'ABS', 'GLUTES',
            'QUADRICEPS', 'HAMSTRINGS', 'ADDUCTORS', 'CALVES', 'NECK', 'FULL_BODY'))
);

-- Filtro por grupo muscular (DEV-0055).
create index ix_exercise_muscle_group on exercise_muscle_group (muscle_group, exercise_id);
