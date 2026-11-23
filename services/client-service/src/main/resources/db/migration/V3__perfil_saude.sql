-- Perfil de saúde do cliente (DEV-0033, FR-08). Dado pessoal sensível (LGPD, art. 11) de que o cliente é
-- dono (BR-01). Exige o perfil do cliente (V2) já criado.
create table client_health (
    -- sub do token do Keycloak; o perfil do cliente precisa existir.
    account_id varchar(64) primary key references client_profile (account_id),
    -- Condições marcadas, sem repetição (garantido pelo domínio). Vazio quer dizer nenhuma.
    conditions varchar(24)[] not null,
    notes varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_client_health_conditions check (
        conditions <@ array['DIABETES', 'HYPERTENSION', 'PREGNANCY', 'RECENT_SURGERY', 'OTHER']::varchar(24)[]),
    constraint ck_client_health_notes check (notes is null or length(btrim(notes)) > 0)
);
