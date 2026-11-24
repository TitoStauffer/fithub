-- Alternativas de alimento no plano (DEV-0082, FR-44): até 2 trocas por item (a secundária e a terciária), cada uma
-- com o alimento e a quantidade própria. Vão junto com o item: regravar as refeições do rascunho apaga e recria.

create table meal_item_alternative (
    version_id uuid not null,
    meal_position int not null,
    item_position int not null,
    -- 0 é a secundária, 1 a terciária.
    position int not null,
    food_id uuid not null references food (id),
    -- Quantidade na unidade base do alimento da alternativa (g ou ml).
    amount numeric(6, 1) not null,
    primary key (version_id, meal_position, item_position, position),
    foreign key (version_id, meal_position, item_position)
        references meal_item (version_id, meal_position, position) on delete cascade,
    constraint ck_meal_item_alternative_position check (position between 0 and 1),
    constraint ck_meal_item_alternative_amount check (amount between 0.1 and 5000)
);

create index ix_meal_item_alternative_food on meal_item_alternative (food_id);
