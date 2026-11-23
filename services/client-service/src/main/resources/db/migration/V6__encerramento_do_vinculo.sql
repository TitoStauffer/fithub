-- Encerramento do vínculo pelo cliente ou pelo profissional (DEV-0037, FR-12, BR-05).
alter table client_link add column ended_at timestamptz;
alter table client_link add column ended_by varchar(16);

alter table client_link
    add constraint ck_client_link_ended_by check (ended_by in ('CLIENT', 'PROFESSIONAL')),
    -- Encerrado sempre com data e autor; ativo, nunca.
    add constraint ck_client_link_ended check ((status = 'ENDED') = (ended_at is not null and ended_by is not null));

-- Lista do profissional: vínculos ativos dele.
create index ix_client_link_professional_active on client_link (professional_id) where status = 'ACTIVE';
