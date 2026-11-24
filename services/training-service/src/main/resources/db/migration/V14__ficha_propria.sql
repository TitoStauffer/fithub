-- Ficha própria do aluno (DEV-0069, FR-52, QUESTÃO-03): o aluno monta a ficha sem personal, na mesma estrutura.
-- Quem escreveu a ficha fica em author: PERSONAL tem o personal_id; CLIENT (a ficha própria) não tem. O padrão
-- PERSONAL mantém compatível quem grava sem conhecer a coluna; a CHECK recusa o autor inconsistente.

alter table workout_plan alter column personal_id drop not null;

alter table workout_plan add column author varchar(16) not null default 'PERSONAL';

alter table workout_plan add constraint ck_workout_plan_author check (
    (author = 'PERSONAL' and personal_id is not null) or (author = 'CLIENT' and personal_id is null)
);

-- Uma ficha própria ativa por aluno. A do personal continua no ux_workout_plan_published (as duas coexistem).
create unique index ux_workout_plan_own_published on workout_plan (client_id)
    where status = 'PUBLISHED' and personal_id is null;
