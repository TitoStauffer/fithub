-- O id do profissional é o sub da conta no Keycloak, o mesmo id da pessoa em todos os serviços (DEV-0149,
-- ADR-009). Cadastros anteriores a esta versão (só em ambientes de desenvolvimento; nada em produção) ganham
-- o id da conta. Nenhuma tabela referencia professional.id.
update professional set id = account_id::uuid where id <> account_id::uuid;

alter table professional
    add constraint ck_professional_id_is_account check (id = account_id::uuid);
