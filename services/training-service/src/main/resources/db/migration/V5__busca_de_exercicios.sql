-- Busca de exercícios por nome (DEV-0055, FR-23): sem diferenciar maiúsculas nem acentos ("triceps" acha
-- "Tríceps"). Uma função só dobra o nome gravado e o texto buscado, para os dois lados baterem.

create function fold_search(value text) returns text
    language sql
    immutable
    parallel safe
    return translate(
        lower(value),
        'áàâãäåéèêëíìîïóòôõöúùûüçñý',
        'aaaaaaeeeeiiiiooooouuuucny');

-- Mantida pelo banco a cada gravação do nome, inclusive nas linhas que já existem.
alter table exercise add column search_name text generated always as (fold_search(name)) stored;
