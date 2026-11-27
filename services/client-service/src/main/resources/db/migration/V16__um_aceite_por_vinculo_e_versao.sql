-- Um aceite por vínculo e versão do termo (DEV-0160, Code Review do PR #343): dois pedidos simultâneos de novo aceite
-- gravariam duas linhas iguais. Antes do novo aceite, cada vínculo tinha um consentimento só, então não há repetição.
alter table consent add constraint uq_consent_link_version unique (link_id, terms_version);
