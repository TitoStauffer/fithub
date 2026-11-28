-- Estorno e chargeback da cobrança (DEV-0182, ADR-015): a cobrança estornada fica no histórico, marcada com a data do
-- primeiro evento de estorno. A marca torna o webhook idempotente: o estorno repetido, ou o chargeback depois do
-- estorno, não encerra o período pago de novo.
alter table subscription_payment add column refunded_at timestamptz;
