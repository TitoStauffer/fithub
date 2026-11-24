-- Busca de alimentos por nome (DEV-0078, FR-35): sem diferenciar maiúsculas nem acentos ("feijao" acha "Feijão").
-- Uma função só dobra o nome gravado e o texto buscado, para os dois lados baterem (a mesma do training-service,
-- DEV-0055; cada serviço tem o próprio banco).

create function fold_search(value text) returns text
    language sql
    immutable
    parallel safe
    return translate(
        lower(value),
        'áàâãäåéèêëíìîïóòôõöúùûüçñý',
        'aaaaaaeeeeiiiiooooouuuucny');

-- Mantida pelo banco a cada gravação do nome, inclusive nas linhas que já existem.
alter table food add column search_name text generated always as (fold_search(name)) stored;
