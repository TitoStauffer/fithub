-- Tabelas técnicas da java-commons (DEV-0014, ADR-003). Aplicadas em cada banco de serviço por um
-- Flyway próprio, com histórico em flyway_fithub_commons_history: a numeração não colide com as
-- migrations do serviço.

-- Outbox transacional: o evento é gravado na mesma transação do dado e publicado depois do commit.
-- sequence dá a ordem exata de gravação (created_at pode empatar no mesmo microssegundo).
create table fithub_outbox_event (
    sequence     bigserial    primary key,
    id           uuid         not null unique,
    exchange     varchar(255) not null,
    routing_key  varchar(255) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null,
    published_at timestamptz,
    attempts     integer      not null default 0,
    last_error   varchar(1000)
);

-- O publicador lê só os pendentes, na ordem de gravação.
create index fithub_outbox_event_pending_idx on fithub_outbox_event (sequence) where published_at is null;
-- O expurgo remove os já publicados depois da retenção.
create index fithub_outbox_event_published_idx on fithub_outbox_event (published_at) where published_at is not null;

-- Consumidor idempotente: um evento é processado uma vez por consumidor.
create table fithub_processed_event (
    consumer     varchar(255) not null,
    event_id     uuid         not null,
    processed_at timestamptz  not null,
    primary key (consumer, event_id)
);

create index fithub_processed_event_processed_at_idx on fithub_processed_event (processed_at);
