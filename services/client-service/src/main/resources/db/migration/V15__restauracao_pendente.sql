-- Marcador de restauração (DEV-0172, ADR-012, item 4): a infra/backup/restore.sh grava uma linha aqui depois de
-- restaurar o banco. Enquanto houver linha, o serviço reaplica a exclusão de cada conta da lista deleted_account e
-- depois a apaga. Sem restauração, a reaplicação não roda: o dado criado depois da exclusão (a conta que também é de
-- profissional pode recriar o perfil de cliente) nunca é apagado por ela (Code Review do PR #316).
-- "if not exists": a restore.sh cria a tabela ao restaurar um backup de antes desta migration.
create table if not exists restore_pending (
    restored_at timestamptz not null
);
