-- Convites do profissional ao cliente (DEV-0035, FR-10).

-- Projeção dos profissionais, mantida pelo consumidor do ProfissionalCadastrado. O id é o sub da conta do
-- profissional (ADR-009). O código pessoal é deste serviço: permanente até o profissional trocá-lo.
create table professional (
    id uuid primary key,
    professional_type varchar(24) not null,
    name varchar(200) not null,
    invite_code char(8) not null,
    registered_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_professional_invite_code unique (invite_code),
    constraint ck_professional_type check (professional_type in ('PERSONAL_TRAINER', 'NUTRITIONIST')),
    -- Alfabeto sem 0/O e 1/I.
    constraint ck_professional_invite_code check (invite_code ~ '^[A-HJ-NP-Z2-9]{8}$')
);

-- Token e código do convite ficam só como hash SHA-256: quem lê o banco não consegue aceitar um convite.
-- Vencido não é situação gravada: é calculado por expires_at.
create table invitation (
    id uuid primary key,
    professional_id uuid not null references professional (id),
    email varchar(254),
    token_hash char(64) not null,
    code_hash char(64) not null,
    status varchar(16) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    cancelled_at timestamptz,
    constraint uq_invitation_token unique (token_hash),
    constraint uq_invitation_code unique (code_hash),
    constraint ck_invitation_status check (status in ('PENDING', 'ACCEPTED', 'CANCELLED')),
    constraint ck_invitation_cancelled check ((status = 'CANCELLED') = (cancelled_at is not null)),
    constraint ck_invitation_validity check (expires_at > created_at)
);

create index ix_invitation_professional on invitation (professional_id, created_at desc);
