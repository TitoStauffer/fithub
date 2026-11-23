-- Aceite do convite: vínculo e consentimento (DEV-0036, FR-11, FR-13, BR-07, NFR-01).

alter table invitation add column accepted_at timestamptz;
alter table invitation
    add constraint ck_invitation_accepted check ((status = 'ACCEPTED') = (accepted_at is not null));

-- Vínculo entre cliente e profissional. Os ids são os sub das contas (ADR-009). O encerramento é a DEV-0037.
create table client_link (
    id uuid primary key,
    client_id uuid not null,
    professional_id uuid not null references professional (id),
    professional_type varchar(24) not null,
    status varchar(16) not null,
    -- Convite aceito; nulo quando o cliente usou o código pessoal do profissional.
    invitation_id uuid references invitation (id),
    started_at timestamptz not null,
    constraint ck_client_link_type check (professional_type in ('PERSONAL_TRAINER', 'NUTRITIONIST')),
    constraint ck_client_link_status check (status in ('ACTIVE', 'ENDED')),
    -- Ninguém se vincula a si mesmo (a mesma conta pode ser cliente e profissional, QUESTÃO-02).
    constraint ck_client_link_not_self check (client_id <> professional_id),
    -- Um convite vira no máximo um vínculo.
    constraint uq_client_link_invitation unique (invitation_id)
);

-- BR-07: no máximo 1 vínculo ativo de cada tipo por cliente, garantido mesmo com aceites simultâneos.
create unique index ux_client_link_active_type on client_link (client_id, professional_type) where status = 'ACTIVE';

-- Consentimento do aceite (NFR-01): data, escopo e versão do termo. Sem IP nem aparelho (minimização).
create table consent (
    id uuid primary key,
    link_id uuid not null references client_link (id),
    client_id uuid not null,
    scope varchar(16)[] not null,
    terms_version varchar(16) not null,
    granted_at timestamptz not null,
    constraint ck_consent_scope check (
        cardinality(scope) > 0
        and scope <@ array['PROFILE', 'HEALTH', 'ASSESSMENTS', 'PHOTOS']::varchar(16)[])
);

create index ix_consent_link on consent (link_id);
