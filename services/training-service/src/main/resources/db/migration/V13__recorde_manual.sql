-- Recorde manual do aluno (DEV-0068, FR-54): uma marca feita fora das séries registradas. Os recordes do aluno são
-- calculados na leitura, das séries e destes lançamentos; não há tabela de recordes derivada para manter.

create table manual_record (
    id uuid primary key,
    -- O sub do aluno (ADR-009).
    client_id uuid not null,
    exercise_id uuid not null references exercise (id),
    load_kg numeric(6, 2) not null,
    reps int not null,
    performed_on date not null,
    created_at timestamptz not null,
    constraint ck_manual_record_load check (load_kg > 0 and load_kg <= 1000 and load_kg * 4 = trunc(load_kg * 4)),
    constraint ck_manual_record_reps check (reps between 1 and 300)
);

create index ix_manual_record_client on manual_record (client_id, exercise_id);
