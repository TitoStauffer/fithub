-- Alimentos e receitas próprios da nutricionista (DEV-0077, FR-36, BR-13). O próprio é arquivado em vez de apagado:
-- sai da busca, mas continua nos planos que o usam. A receita é um alimento próprio com ingredientes e rendimento; os
-- valores por 100 g dela são calculados ao salvar e gravados no próprio alimento.

alter table food add column archived_at timestamptz;
-- Rendimento total da receita, em gramas; vazio no alimento que não é receita.
alter table food add column recipe_yield_g numeric(6, 1);

alter table food add constraint ck_food_archived check (archived_at is null or kind = 'OWN');
alter table food add constraint ck_food_recipe check (
    recipe_yield_g is null or (kind = 'OWN' and recipe_yield_g between 1 and 20000));

create table food_recipe_ingredient (
    recipe_id uuid not null references food (id) on delete cascade,
    -- Ordem do ingrediente na receita.
    position int not null,
    ingredient_id uuid not null references food (id),
    -- Quantidade na unidade base do ingrediente (g ou ml).
    amount numeric(6, 1) not null,
    primary key (recipe_id, position),
    constraint ck_food_recipe_ingredient_position check (position between 0 and 29),
    constraint ck_food_recipe_ingredient_amount check (amount between 0.1 and 5000),
    constraint ck_food_recipe_ingredient_self check (ingredient_id <> recipe_id)
);

create index ix_food_recipe_ingredient on food_recipe_ingredient (ingredient_id);
