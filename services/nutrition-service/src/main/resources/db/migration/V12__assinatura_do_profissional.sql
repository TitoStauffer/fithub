-- Projeção do AssinaturaAlterada do account-service (DEV-0101, FR-06): sem assinatura válida, o profissional não
-- cria nem edita prescrições. Uma linha por profissional; o evento mais novo por occurred_at vence.
create table professional_subscription (
    professional_id uuid primary key,
    active boolean not null,
    -- Até quando prescreve; com ela, o bloqueio começa sem esperar outro evento. Nula: vale o active.
    valid_until timestamptz,
    occurred_at timestamptz not null
);
