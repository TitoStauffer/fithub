-- Nome do profissional (DEV-0030): vem do perfil do Keycloak no cadastro e vai no evento
-- ProfissionalCadastrado. Até o máximo do contrato do evento (200 caracteres).
alter table professional add column name varchar(200);

-- Cadastros feitos antes desta migration (só ambientes de desenvolvimento) ficam com um nome
-- provisório; a coluna passa a ser obrigatória.
update professional set name = 'Profissional' where name is null;
alter table professional alter column name set not null;
alter table professional add constraint ck_professional_name check (length(btrim(name)) > 0);

-- Code Review do PR #199 (LOW): com a categoria nula, "registration_category in ('G', 'P')" dava NULL
-- e o CHECK aceitava um PERSONAL sem categoria. A categoria passa a ser exigida explicitamente.
alter table professional drop constraint ck_professional_council;
alter table professional add constraint ck_professional_council check (
    (type = 'PERSONAL' and council = 'CREF' and registration_category is not null
        and registration_category in ('G', 'P'))
    or (type = 'NUTRITIONIST' and council = 'CRN' and registration_category is null));
