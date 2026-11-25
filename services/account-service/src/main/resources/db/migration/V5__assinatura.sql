-- Assinatura do profissional (DEV-0099, FR-05, FR-06). O estado (teste, ativa, vencida) não é guardado: sai das
-- datas na leitura. Uma assinatura por profissional.
create table subscription (
    professional_id uuid primary key references professional (id),
    plan varchar(32) not null,
    -- Fim do teste grátis, que começa no cadastro.
    trial_ends_at timestamptz not null,
    -- Até quando o período pago vale; vazio enquanto nada foi pago (o pagamento é da DEV-0100).
    paid_until timestamptz,
    created_at timestamptz not null,
    constraint ck_subscription_plan check (plan in ('PROFESSIONAL_MONTHLY')),
    constraint ck_subscription_trial check (trial_ends_at > created_at)
);

-- Cadastros anteriores a esta versão ganham o teste de 14 dias a partir de agora.
insert into subscription (professional_id, plan, trial_ends_at, paid_until, created_at)
select id, 'PROFESSIONAL_MONTHLY', now() + interval '14 days', null, now()
  from professional;
