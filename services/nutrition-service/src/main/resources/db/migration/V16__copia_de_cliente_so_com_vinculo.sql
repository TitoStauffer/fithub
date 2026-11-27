-- Minimização (DEV-0153): a cópia dos dados básicos do cliente só fica enquanto ele tem vínculo ativo neste serviço.
-- Apaga as cópias de quem não tem vínculo ativo; elas voltam pelo ClienteAtualizado que o client-service republica na
-- próxima ativação de vínculo.
delete from client c
 where not exists (select 1 from client_link l where l.client_id = c.client_id and l.status = 'ACTIVE');
