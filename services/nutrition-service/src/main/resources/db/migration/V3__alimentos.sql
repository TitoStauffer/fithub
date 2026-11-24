-- Base de alimentos da nutricionista (DEV-0074, FR-35, FR-37, FR-38): da Tabela TACO (compartilhada) ou próprios de
-- uma nutricionista (BR-13). Energia e macronutrientes por 100 da unidade base (100 g ou 100 ml).

create table food (
    id uuid primary key,
    kind varchar(8) not null,
    -- O sub da nutricionista dona (ADR-009); vazio na TACO.
    owner_id uuid,
    name varchar(120) not null,
    base_unit varchar(2) not null,
    energy_kcal numeric(4, 1) not null,
    protein_g numeric(5, 2) not null,
    carbs_g numeric(5, 2) not null,
    fat_g numeric(5, 2) not null,
    -- A TACO não traz fibra para todo alimento.
    fiber_g numeric(5, 2),
    vegan boolean not null,
    -- Fonte e id do item importado, para a importação ser reexecutável.
    external_source varchar(200),
    external_id varchar(200),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_food_kind check (kind in ('TACO', 'OWN')),
    constraint ck_food_owner check ((kind = 'OWN') = (owner_id is not null)),
    constraint ck_food_source check (
        (kind = 'TACO' and external_source is not null and external_id is not null)
        or (kind = 'OWN' and external_source is null and external_id is null)),
    constraint ck_food_name check (char_length(btrim(name)) >= 2),
    constraint ck_food_base_unit check (base_unit in ('G', 'ML')),
    constraint ck_food_energy check (energy_kcal between 0 and 900),
    constraint ck_food_macros check (
        protein_g between 0 and 100 and carbs_g between 0 and 100 and fat_g between 0 and 100
        and protein_g + carbs_g + fat_g <= 100),
    constraint ck_food_fiber check (fiber_g is null or fiber_g between 0 and 100)
);

create unique index ux_food_external on food (external_source, external_id) where external_source is not null;
create index ix_food_owner on food (owner_id) where owner_id is not null;
