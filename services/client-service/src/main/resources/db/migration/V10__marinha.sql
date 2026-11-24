-- % de gordura estimado pela fórmula da Marinha dos EUA (DEV-0046, FR-17), na avaliação online, com a altura do
-- perfil usada na conta.
alter table body_assessment
    add column navy_percent numeric(4, 1),
    add column navy_height_cm smallint;

alter table body_assessment
    add constraint ck_body_assessment_navy check (
        (navy_percent is null) = (navy_height_cm is null)
        and (navy_percent is null or assessment_type = 'ONLINE'));
