-- Auditoria de acesso de profissionais a dados sensíveis (DEV-0170, NFR-03, ADR-012): cada leitura das restrições
-- de mobilidade do aluno pelo personal vinculado. Guarda quem leu, de quem, o quê e quando, nunca o conteúdo. Fica 6
-- meses e sai por expurgo; continua depois da exclusão da conta, até o prazo.
create table professional_access_log (
    id uuid primary key,
    professional_id uuid not null,
    client_id uuid not null,
    resource varchar(32) not null,
    accessed_at timestamptz not null,
    constraint ck_professional_access_log_resource check (resource in ('MOBILITY_RESTRICTIONS'))
);

create index ix_professional_access_log_client on professional_access_log (client_id, accessed_at desc);
create index ix_professional_access_log_accessed_at on professional_access_log (accessed_at);
