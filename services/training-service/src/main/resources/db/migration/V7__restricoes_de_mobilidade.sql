-- Restrições de mobilidade do aluno (DEV-0058, FR-26): região do corpo + descrição. São do aluno (BR-01); o
-- personal vinculado registra, e só o lado personal e o próprio aluno veem (BR-03).

create table mobility_restriction (
    id uuid primary key,
    -- O sub da conta do aluno (ADR-009).
    client_id uuid not null,
    region varchar(16) not null,
    description varchar(500) not null,
    -- O sub do personal que registrou ou editou por último.
    recorded_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_mobility_restriction_region check (
        region in ('CERVICAL', 'SHOULDER', 'ELBOW', 'WRIST_HAND', 'THORACIC', 'LUMBAR', 'HIP', 'KNEE', 'ANKLE_FOOT')),
    constraint ck_mobility_restriction_description check (char_length(btrim(description)) >= 1)
);

create index ix_mobility_restriction_client on mobility_restriction (client_id, created_at);
