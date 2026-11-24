import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import type { FoodSearch, FoodSummary } from '@/lib/nutritionApi';

import { per100Text } from './foodForm';
import { FOODS_KEY, useNutritionApi } from './useNutritionApi';

const PAGE_SIZE = 20;

/**
 * Catálogo de alimentos da nutricionista (DEV-0079, FR-35, FR-36, FR-38): busca por nome com o filtro vegano, em
 * páginas, mostrando os valores por 100 e a origem (TACO ou próprio). O próprio pode ser editado e arquivado.
 */
export function FoodsPage() {
    const api = useNutritionApi();
    const [draft, setDraft] = useState<FoodSearch>({ name: '', vegan: '' });
    const [search, setSearch] = useState<FoodSearch>({ name: '', vegan: '', page: 0 });

    const results = useQuery({
        queryKey: [...FOODS_KEY, search],
        queryFn: () => api.searchFoods({ ...search, size: PAGE_SIZE }),
        placeholderData: keepPreviousData,
    });

    const submit = (event: FormEvent) => {
        event.preventDefault();
        setSearch({ ...draft, page: 0 });
    };

    const page = search.page ?? 0;
    const total = results.data?.total ?? 0;
    const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    return (
        <section className="space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold">Alimentos</h3>
                <Button asChild size="sm">
                    <Link to="/app/alimentos/novo">Novo alimento ou receita</Link>
                </Button>
            </div>
            <form role="search" onSubmit={submit} className="flex flex-wrap items-end gap-3">
                <label className="space-y-1">
                    <span className="block text-sm font-medium">Nome</span>
                    <input
                        className="rounded border px-2 py-1"
                        value={draft.name}
                        onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                    />
                </label>
                <label className="space-y-1">
                    <span className="block text-sm font-medium">Vegano</span>
                    <select
                        className="rounded border px-2 py-1"
                        value={draft.vegan}
                        onChange={(event) =>
                            setDraft({ ...draft, vegan: event.target.value as FoodSearch['vegan'] })
                        }
                    >
                        <option value="">Todos</option>
                        <option value="true">Só veganos</option>
                        <option value="false">Só não veganos</option>
                    </select>
                </label>
                <Button type="submit" variant="outline">
                    Buscar
                </Button>
            </form>

            {results.isPending ? (
                <p role="status">Carregando alimentos…</p>
            ) : results.isError ? (
                <div role="alert" className="space-y-2">
                    <p>{messageOf(results.error)}</p>
                    <Button variant="outline" size="sm" onClick={() => void results.refetch()}>
                        Tentar de novo
                    </Button>
                </div>
            ) : (results.data.items ?? []).length === 0 ? (
                <p>Nenhum alimento encontrado com esses filtros.</p>
            ) : (
                <>
                    <p className="text-sm text-muted-foreground">
                        {total === 1 ? '1 alimento' : `${total} alimentos`}
                    </p>
                    <ul className="space-y-3">
                        {(results.data.items ?? []).map((food) => (
                            <li key={food.id}>
                                <FoodCard food={food} />
                            </li>
                        ))}
                    </ul>
                    <nav aria-label="Páginas" className="flex items-center gap-3">
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={page === 0}
                            onClick={() => setSearch({ ...search, page: page - 1 })}
                        >
                            Anterior
                        </Button>
                        <span className="text-sm">
                            Página {page + 1} de {pages}
                        </span>
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={page + 1 >= pages}
                            onClick={() => setSearch({ ...search, page: page + 1 })}
                        >
                            Próxima
                        </Button>
                    </nav>
                </>
            )}
        </section>
    );
}

function FoodCard({ food }: { food: FoodSummary }) {
    const own = food.kind === 'OWN';
    const unit = food.baseUnit === 'ML' ? '100 ml' : '100 g';
    return (
        <article aria-label={food.name} className="space-y-2 rounded border p-3">
            <header className="flex flex-wrap items-center gap-2">
                <h4 className="font-medium">{food.name}</h4>
                <span className="rounded bg-muted px-2 text-xs">
                    {own ? (food.recipe ? 'Receita própria' : 'Próprio') : 'TACO'}
                </span>
                {food.vegan ? <span className="rounded bg-muted px-2 text-xs">Vegano</span> : null}
            </header>
            <p className="text-sm">
                Por {unit}: {per100Text(food)}
            </p>
            {own ? <OwnActions food={food} /> : null}
        </article>
    );
}

/** Editar e arquivar o próprio; arquivar pede confirmação. */
function OwnActions({ food }: { food: FoodSummary }) {
    const api = useNutritionApi();
    const queryClient = useQueryClient();
    const [confirming, setConfirming] = useState(false);
    const archive = useMutation({
        mutationFn: () => api.archiveFood(food.id ?? ''),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: FOODS_KEY }),
    });

    return (
        <div className="flex flex-wrap items-center gap-2">
            <Button asChild variant="outline" size="sm">
                <Link to={`/app/alimentos/${food.id ?? ''}/editar`}>Editar</Link>
            </Button>
            {confirming ? (
                <>
                    <span className="text-sm">
                        Arquivar? Ele sai da busca, mas os planos que já o usam continuam mostrando.
                    </span>
                    <Button size="sm" disabled={archive.isPending} onClick={() => archive.mutate()}>
                        {archive.isPending ? 'Arquivando…' : 'Confirmar arquivamento'}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
                        Cancelar
                    </Button>
                </>
            ) : (
                <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
                    Arquivar
                </Button>
            )}
            {archive.isError ? <p role="alert">{messageOf(archive.error)}</p> : null}
        </div>
    );
}
