-- Tabelas técnicas da java-commons (DEV-0014, ADR-003). Aplicadas em cada banco de serviço por um
-- Flyway próprio, com histórico em flyway_fithub_commons_history: a numeração não colide com as
-- migrations do serviço.

-- Outbox transacional: o evento é gravado na mesma transação do dado e publicado depois do commit.
create table fithub_outbox_event (
    id           uuid primary key,
    exchange     varchar(255) not null,
    routing_key  varchar(255) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null,
    published_at timestamptz,
    attempts     integer      not null default 0,
    last_error   varchar(1000)
);

-- O publicador lê só os pendentes, em ordem de criação.
create index fithub_outbox_event_pending_idx on fithub_outbox_event (created_at, id) where published_at is null;

-- Consumidor idempotente: um evento é processado uma vez por consumidor.
create table fithub_processed_event (
    consumer     varchar(255) not null,
    event_id     uuid         not null,
    processed_at timestamptz  not null,
    primary key (consumer, event_id)
);
