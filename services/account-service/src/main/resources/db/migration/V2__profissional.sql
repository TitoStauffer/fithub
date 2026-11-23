-- Cadastro do profissional (DEV-0029). Cada conta do Keycloak tem no máximo um cadastro, e cada
-- profissional é dono de uma organização de uma pessoa (academias ficam fora do MVP).

create table organization (
    id uuid primary key,
    owner_account_id varchar(64) not null,
    created_at timestamptz not null
);

create table professional (
    id uuid primary key,
    -- sub do token do Keycloak.
    account_id varchar(64) not null,
    type varchar(16) not null,
    council varchar(8) not null,
    registration_number varchar(6) not null,
    registration_category varchar(1),
    registration_state char(2) not null,
    situation varchar(16) not null,
    organization_id uuid not null references organization (id),
    role_status varchar(16) not null,
    created_at timestamptz not null,
    -- Um cadastro por conta, inclusive com pedidos simultâneos.
    constraint uk_professional_account unique (account_id),
    constraint ck_professional_type check (type in ('PERSONAL', 'NUTRITIONIST')),
    constraint ck_professional_council check (
        (type = 'PERSONAL' and council = 'CREF' and registration_category in ('G', 'P'))
        or (type = 'NUTRITIONIST' and council = 'CRN' and registration_category is null)),
    constraint ck_professional_number check (registration_number ~ '^[0-9]{1,6}$'),
    constraint ck_professional_situation check (situation in ('DECLARED')),
    constraint ck_professional_role_status check (role_status in ('PENDING', 'ASSIGNED'))
);

-- A retentativa de atribuição de papel lê só os pendentes, do mais antigo para o mais novo.
create index ix_professional_role_pending on professional (created_at) where role_status = 'PENDING';
