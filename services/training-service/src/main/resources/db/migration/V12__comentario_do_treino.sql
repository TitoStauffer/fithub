-- Comentário e sensação do treino (DEV-0067, FR-49): o aluno conta como foi. Opcionais; corrigíveis no mesmo prazo
-- das séries (BR-08).

alter table workout_session
    add column comment varchar(500),
    -- 1 = muito fácil, 5 = exaustivo.
    add column feeling int,
    add constraint ck_workout_session_feeling check (feeling is null or feeling between 1 and 5);
