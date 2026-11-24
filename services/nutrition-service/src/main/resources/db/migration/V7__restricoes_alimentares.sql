-- Restrições alimentares e preferências do paciente (DEV-0080, FR-40): tipo, descrição e os termos que o alerta do
-- plano (DEV-0084) procura no nome dos alimentos. São do paciente (BR-01): a nutricionista vinculada registra, e só o
-- lado nutri e o próprio paciente veem (BR-03).

create table food_restriction (
    id uuid primary key,
    -- O sub do paciente (ADR-009).
    client_id uuid not null,
    kind varchar(16) not null,
    description varchar(200) not null,
    -- Termos procurados no nome dos alimentos, de 1 a 10 (ex.: leite, queijo, lactose).
    terms text[] not null,
    -- O sub da nutricionista que registrou ou editou por último.
    recorded_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_food_restriction_kind check (kind in ('ALLERGY', 'INTOLERANCE', 'PREFERENCE')),
    constraint ck_food_restriction_description check (char_length(btrim(description)) >= 2),
    constraint ck_food_restriction_terms check (cardinality(terms) between 1 and 10)
);

create index ix_food_restriction_client on food_restriction (client_id, created_at);
