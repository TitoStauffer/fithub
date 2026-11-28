-- Hash do e-mail de quem excluiu a conta (DEV-0174, ADR-012, item 1): sha256 do e-mail em minúsculas, em hexadecimal.
-- Depois de restaurar um backup, a reaplicação da exclusão não tem mais o token, e é por ele que limpa o e-mail dos
-- convites pendentes que voltaram. A lista guarda só o hash, nunca o e-mail, e sai em 30 dias, com os backups.
-- "if not exists": a restore.sh cria a coluna ao restaurar um backup de antes desta migration, para regravar nela a
-- lista de agora.
alter table deleted_account add column if not exists email_hash char(64);
