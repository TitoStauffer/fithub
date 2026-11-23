-- Auditoria das leituras do perfil de saúde pelo profissional vinculado (DEV-0040, NFR-03): quem leu, de quem
-- e quando. Não guarda o dado lido.
create table health_access_log (
    id uuid primary key,
    professional_id uuid not null,
    client_id uuid not null,
    accessed_at timestamptz not null
);

create index ix_health_access_log_client on health_access_log (client_id, accessed_at desc);
