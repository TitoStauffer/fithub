-- Prescrição por exercício na divisão da ficha (DEV-0060, FR-30, FR-31): séries, repetições fixas ou em faixa,
-- carga sugerida, descanso, observação, técnica e até 3 alternativas. Os itens são da versão: a publicada não muda.

create table workout_item (
    version_id uuid not null,
    division_position int not null,
    position int not null,
    exercise_id uuid not null references exercise (id),
    sets int not null,
    -- Repetições fixas: mínimo igual ao máximo; faixa (8–12): mínimo menor que o máximo.
    reps_min int not null,
    reps_max int not null,
    load_kg numeric(5, 1),
    rest_seconds int,
    note varchar(500),
    technique varchar(16),
    primary key (version_id, division_position, position),
    foreign key (version_id, division_position) references workout_division (version_id, position) on delete cascade,
    constraint ck_workout_item_position check (position between 0 and 29),
    constraint ck_workout_item_sets check (sets between 1 and 20),
    constraint ck_workout_item_reps check (reps_min between 1 and 100 and reps_max between reps_min and 100),
    constraint ck_workout_item_load check (load_kg is null or (load_kg between 0 and 1000 and load_kg * 2 = trunc(load_kg * 2))),
    constraint ck_workout_item_rest check (rest_seconds is null or rest_seconds between 0 and 600),
    constraint ck_workout_item_technique check (
        technique is null or technique in ('BI_SET', 'TRI_SET', 'DROP_SET', 'REST_PAUSE'))
);

create index ix_workout_item_exercise on workout_item (exercise_id);

create table workout_item_alternative (
    version_id uuid not null,
    division_position int not null,
    item_position int not null,
    position int not null,
    exercise_id uuid not null references exercise (id),
    primary key (version_id, division_position, item_position, position),
    foreign key (version_id, division_position, item_position)
        references workout_item (version_id, division_position, position) on delete cascade,
    constraint ck_workout_item_alternative_position check (position between 0 and 2),
    constraint uq_workout_item_alternative unique (version_id, division_position, item_position, exercise_id)
);
