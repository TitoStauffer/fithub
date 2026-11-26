-- Pagamento da assinatura pelo checkout do Asaas (DEV-0100, PRD seção 9.2). Só ids do Asaas, valores e datas: o
-- Asaas coleta os dados do pagador (CPF/CNPJ, cartão), e o FitHub não guarda nenhum deles.

-- Cada checkout criado para um profissional, com o valor da primeira cobrança e se ele entrou no preço de lançamento.
create table payment_checkout (
    id varchar(100) primary key,
    professional_id uuid not null references professional (id),
    launch boolean not null,
    value numeric(10, 2) not null,
    created_at timestamptz not null,
    constraint ck_payment_checkout_value check (value > 0)
);

create index ix_payment_checkout_professional on payment_checkout (professional_id);

-- A assinatura do Asaas que um checkout pago gerou, ligada pelo checkoutSession dela. O preço de lançamento termina
-- quando o valor passa ao cheio (launch_ended_at).
create table payment_subscription (
    id varchar(100) primary key,
    checkout_id varchar(100) not null references payment_checkout (id),
    professional_id uuid not null references professional (id),
    created_at timestamptz not null,
    launch_ended_at timestamptz,
    constraint uq_payment_subscription_checkout unique (checkout_id)
);

-- Histórico das cobranças confirmadas: estende o período pago e alimenta as métricas de conversão e retenção (DEV-0113).
-- O id da cobrança torna o webhook idempotente: o Asaas entrega pelo menos uma vez, e o CONFIRMED e o RECEIVED da
-- mesma cobrança contam uma vez só.
create table subscription_payment (
    payment_id varchar(100) primary key,
    subscription_id varchar(100) not null references payment_subscription (id),
    professional_id uuid not null references professional (id),
    value numeric(10, 2) not null,
    due_date date not null,
    confirmed_at timestamptz not null
);

create index ix_subscription_payment_subscription on subscription_payment (subscription_id);
create index ix_subscription_payment_due on subscription_payment (due_date);
