-- % de gordura de bioimpedância informado manualmente (DEV-0047, FR-18), em qualquer tipo de avaliação.
alter table body_assessment add column bioimpedance_percent numeric(3, 1);

alter table body_assessment
    add constraint ck_body_assessment_bioimpedance check (bioimpedance_percent between 3 and 70);
