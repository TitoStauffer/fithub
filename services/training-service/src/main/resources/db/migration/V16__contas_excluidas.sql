-- Contas excluídas pelo cliente (DEV-0162, ADR-012): só o id e quando. Serve para reaplicar a exclusão se um backup
-- anterior for restaurado (DEV-0108) e sai depois de 30 dias, junto com os backups que ainda têm o dado.
create table deleted_account (
    client_id uuid primary key,
    deleted_at timestamptz not null
);

create index ix_deleted_account_deleted_at on deleted_account (deleted_at);
