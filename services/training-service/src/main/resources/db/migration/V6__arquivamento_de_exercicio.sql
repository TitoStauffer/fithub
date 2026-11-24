-- Arquivamento do exercício próprio (DEV-0056, FR-24): o dono "exclui" sem apagar, porque fichas antigas continuam
-- mostrando o exercício. Arquivado sai da busca e não muda mais.

alter table exercise
    add column archived_at timestamptz,
    -- O catálogo é de todos: só exercício próprio pode ser arquivado.
    add constraint ck_exercise_archived_own check (archived_at is null or kind = 'OWN');
