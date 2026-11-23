-- Projeções dos eventos do client-service (DEV-0151, ADR-003): vínculos de nutricionista e dados básicos dos
-- clientes. Os ids são os sub das contas (ADR-009).

-- Vínculos de nutricionista (BR-03). Encerrado não volta a ficar ativo, mesmo com eventos fora de ordem.
create table client_link (
    link_id uuid primary key,
    client_id uuid not null,
    professional_id uuid not null,
    status varchar(16) not null,
    activated_at timestamptz,
    ended_at timestamptz,
    constraint ck_client_link_status check (status in ('ACTIVE', 'ENDED')),
    constraint ck_client_link_ended check ((status = 'ENDED') = (ended_at is not null))
);

create index ix_client_link_access on client_link (professional_id, client_id) where status = 'ACTIVE';

-- Dados básicos do cliente (ClienteAtualizado). Sem saúde, objetivo ou altura.
create table client (
    client_id uuid primary key,
    name varchar(200) not null,
    birth_date date,
    gender varchar(50),
    updated_at timestamptz not null
);
