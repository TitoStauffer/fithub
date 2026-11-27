-- Aceite do termo do perfil de saúde que o próprio cliente preenche (DEV-0179): base legal do dado de saúde (LGPD
-- art. 11, I). Um aceite por conta e versão do termo, com o momento; sem IP nem aparelho (minimização). Sai junto com a
-- conta na exclusão (ADR-012).
create table health_consent (
    account_id varchar(64) not null references client_profile (account_id),
    terms_version varchar(16) not null,
    granted_at timestamptz not null,
    constraint pk_health_consent primary key (account_id, terms_version)
);
