-- Fotos de evolução das avaliações (DEV-0048, FR-20, NFR-02): uma por pose. O arquivo fica no bucket privado; aqui
-- só a chave do objeto, que nunca sai do serviço.
create table assessment_photo (
    assessment_id uuid not null references body_assessment (id) on delete cascade,
    client_id uuid not null,
    pose varchar(8) not null,
    object_key varchar(255) not null,
    uploaded_at timestamptz not null,
    primary key (assessment_id, pose),
    constraint ck_assessment_photo_pose check (pose in ('FRONT', 'SIDE', 'BACK'))
);

-- Cada objeto é de no máximo uma foto; o expurgo consulta por chave.
create unique index ux_assessment_photo_object_key on assessment_photo (object_key);
