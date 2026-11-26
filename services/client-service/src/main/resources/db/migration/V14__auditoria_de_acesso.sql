-- Auditoria de acesso de profissionais a dados sensíveis (DEV-0106, NFR-03, ADR-012): o registro de leitura da
-- saúde (V7) passa a dizer o que foi lido. Os registros antigos eram todos da saúde. Fica 6 meses e sai por expurgo.
alter table health_access_log add column resource varchar(16) not null default 'HEALTH';

alter table health_access_log
    add constraint ck_health_access_log_resource check (resource in ('HEALTH', 'ASSESSMENTS', 'PHOTOS'));

create index ix_health_access_log_accessed_at on health_access_log (accessed_at);
