-- Dobras cutâneas e % de gordura por Jackson & Pollock (DEV-0045, FR-16). Dobras só na avaliação presencial;
-- o % é calculado uma vez, na gravação.
alter table body_assessment
    add column skinfold_chest_mm numeric(3, 1),
    add column skinfold_midaxillary_mm numeric(3, 1),
    add column skinfold_triceps_mm numeric(3, 1),
    add column skinfold_subscapular_mm numeric(3, 1),
    add column skinfold_abdomen_mm numeric(3, 1),
    add column skinfold_suprailiac_mm numeric(3, 1),
    add column skinfold_thigh_mm numeric(3, 1),
    add column pollock3_percent numeric(4, 1),
    add column pollock7_percent numeric(4, 1);

alter table body_assessment
    add constraint ck_body_assessment_skinfolds check (
        coalesce(skinfold_chest_mm, 1) between 1 and 80 and coalesce(skinfold_midaxillary_mm, 1) between 1 and 80
        and coalesce(skinfold_triceps_mm, 1) between 1 and 80 and coalesce(skinfold_subscapular_mm, 1) between 1 and 80
        and coalesce(skinfold_abdomen_mm, 1) between 1 and 80 and coalesce(skinfold_suprailiac_mm, 1) between 1 and 80
        and coalesce(skinfold_thigh_mm, 1) between 1 and 80),
    -- Dobras e Pollock só na presencial.
    add constraint ck_body_assessment_skinfolds_presential check (
        assessment_type = 'PRESENTIAL'
        or (skinfold_chest_mm is null and skinfold_midaxillary_mm is null and skinfold_triceps_mm is null
            and skinfold_subscapular_mm is null and skinfold_abdomen_mm is null and skinfold_suprailiac_mm is null
            and skinfold_thigh_mm is null and pollock3_percent is null and pollock7_percent is null));
