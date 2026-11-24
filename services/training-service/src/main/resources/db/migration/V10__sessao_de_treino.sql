-- Sessão de treino do aluno (DEV-0065, FR-29, FR-47): qual divisão da ficha ele fez, em qual versão (BR-09). O
-- próximo treino é a divisão seguinte à da última sessão finalizada.

create table workout_session (
    -- Gerado pelo app: iniciar de novo com o mesmo id não cria outra sessão.
    id uuid primary key,
    -- O sub do aluno (ADR-009).
    client_id uuid not null,
    plan_id uuid not null references workout_plan (id),
    version_id uuid not null references workout_plan_version (id),
    division_position int not null,
    started_at timestamptz not null,
    finished_at timestamptz,
    constraint ck_workout_session_division check (division_position between 0 and 6),
    constraint ck_workout_session_finished check (finished_at is null or finished_at >= started_at)
);

-- Uma sessão aberta por ficha.
create unique index ux_workout_session_open on workout_session (plan_id) where finished_at is null;
create index ix_workout_session_plan on workout_session (plan_id, finished_at);
create index ix_workout_session_client on workout_session (client_id, started_at);
