-- Crédito da mídia importada (DEV-0054, ADR-010): imagem ou vídeo do wger entra só com a licença e o autor
-- dele, que muitas vezes não são os do texto do exercício.

alter table exercise
    add column media_license varchar(200),
    add column media_author varchar(200),
    -- Licença e autor da mídia vêm juntos, ou nenhum.
    add constraint ck_exercise_media_credit_pair check ((media_license is null) = (media_author is null)),
    -- Exercício próprio não tem crédito de mídia; no importado, mídia e crédito andam juntos.
    add constraint ck_exercise_media_credit_match check (
        (external_source is null and media_license is null)
        or (external_source is not null and (media_url is null) = (media_license is null)));
