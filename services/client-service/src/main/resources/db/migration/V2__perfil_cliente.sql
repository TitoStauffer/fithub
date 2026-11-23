-- Perfil do cliente (DEV-0032, FR-07). Dado pessoal de que o cliente é dono (BR-01): um perfil por conta
-- do Keycloak. A idade não é gravada: é calculada a partir da data de nascimento (FR-09).
create table client_profile (
    -- sub do token do Keycloak.
    account_id varchar(64) primary key,
    name varchar(200) not null,
    birth_date date not null,
    gender varchar(24) not null,
    height_cm smallint not null,
    goal varchar(24) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_client_profile_name check (length(btrim(name)) > 0),
    constraint ck_client_profile_gender check (gender in ('FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    constraint ck_client_profile_height check (height_cm between 100 and 250),
    constraint ck_client_profile_goal check (goal in ('WEIGHT_LOSS', 'HYPERTROPHY', 'CONDITIONING', 'HEALTH', 'OTHER'))
);
