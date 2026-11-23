-- Avaliação corporal (DEV-0044, FR-14, FR-15). Dado do cliente (BR-01), visto pelos profissionais vinculados
-- (BR-04). Imutável: não há update; o cliente exclui.
create table body_assessment (
    id uuid primary key,
    client_id uuid not null,
    assessment_type varchar(16) not null,
    measured_on date not null,
    weight_kg numeric(4, 1) not null,
    neck_cm numeric(4, 1),
    chest_cm numeric(4, 1),
    waist_cm numeric(4, 1),
    abdomen_cm numeric(4, 1),
    hip_cm numeric(4, 1),
    arm_cm numeric(4, 1),
    forearm_cm numeric(4, 1),
    thigh_cm numeric(4, 1),
    calf_cm numeric(4, 1),
    sex_for_formulas varchar(8),
    author_id uuid not null,
    author_role varchar(16) not null,
    created_at timestamptz not null,
    constraint ck_body_assessment_type check (assessment_type in ('PRESENTIAL', 'ONLINE')),
    constraint ck_body_assessment_author check (author_role in ('CLIENT', 'PROFESSIONAL')),
    -- Presencial é do profissional; online, do cliente (FR-15).
    constraint ck_body_assessment_type_author check ((assessment_type = 'PRESENTIAL') = (author_role = 'PROFESSIONAL')),
    constraint ck_body_assessment_sex check (sex_for_formulas in ('FEMALE', 'MALE')),
    constraint ck_body_assessment_weight check (weight_kg between 20 and 400),
    constraint ck_body_assessment_circumferences check (
        coalesce(neck_cm, 10) between 10 and 300 and coalesce(chest_cm, 10) between 10 and 300
        and coalesce(waist_cm, 10) between 10 and 300 and coalesce(abdomen_cm, 10) between 10 and 300
        and coalesce(hip_cm, 10) between 10 and 300 and coalesce(arm_cm, 10) between 10 and 300
        and coalesce(forearm_cm, 10) between 10 and 300 and coalesce(thigh_cm, 10) between 10 and 300
        and coalesce(calf_cm, 10) between 10 and 300)
);

create index ix_body_assessment_client on body_assessment (client_id, measured_on desc, created_at desc);
