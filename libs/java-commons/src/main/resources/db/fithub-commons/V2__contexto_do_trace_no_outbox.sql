-- Contexto do trace da requisição que gravou o evento (DEV-0109), no formato W3C traceparent
-- (00-<traceId>-<spanId>-<flags>, 55 caracteres). O publicador manda no cabeçalho da mensagem, e o
-- consumidor continua o mesmo trace. Nulo quando não havia trace (por exemplo, numa tarefa agendada).
-- Só identificadores aleatórios do trace: nenhum dado pessoal.
alter table fithub_outbox_event add column trace_parent varchar(55);
