-- Lista dos logins apagados porque o cliente excluiu a conta (DEV-0173, ADR-012, item 4). Depois de restaurar um
-- backup, o Keycloak volta com o usuário de quem excluiu a conta depois do backup; o account-service reapaga cada login
-- desta lista. O id sai depois de 30 dias, quando nenhum backup tem mais o usuário. Mesmo formato da lista dos outros
-- serviços, que a infra/backup/restore.sh preserva.
-- "if not exists": a restore.sh cria as duas tabelas ao restaurar um backup de antes desta migration.
create table if not exists deleted_account (
    client_id uuid primary key,
    deleted_at timestamptz not null
);

-- Marcador de restauração (DEV-0172): a restore.sh grava uma linha; o account-service reapaga os logins da lista e a
-- apaga. Sem restauração, nada é reapagado.
create table if not exists restore_pending (
    restored_at timestamptz not null
);
