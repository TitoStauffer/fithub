import { useQuery } from '@tanstack/react-query';
import { useState, type KeyboardEvent } from 'react';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { FOODS_KEY, useNutritionApi } from '@/features/foods/useNutritionApi';

import type { PickedFood } from './mealPlanForm';

/** Busca na base de alimentos (DEV-0078) para escolher um alimento; mostra até 10 resultados. */
export function FoodPicker({
    label,
    onPick,
}: {
    /** Rótulo do campo de busca, que diz para onde vai o alimento. */
    label: string;
    onPick: (food: PickedFood) => void;
}) {
    const api = useNutritionApi();
    const [draft, setDraft] = useState('');
    const [name, setName] = useState<string>();
    const results = useQuery({
        queryKey: [...FOODS_KEY, 'picker', name],
        queryFn: () => api.searchFoods({ name, size: 10 }),
        enabled: name !== undefined,
    });

    // Sem <form>: o buscador fica dentro do formulário do plano, e formulário aninhado não vale em HTML.
    const search = () => setName(draft.trim());
    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            search();
        }
    };

    return (
        <div className="space-y-1">
            <div className="flex gap-2">
                <input
                    aria-label={label}
                    className="rounded border px-2 py-1"
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={onKeyDown}
                />
                <Button type="button" variant="outline" size="sm" onClick={search}>
                    Buscar
                </Button>
            </div>
            {results.isFetching ? <p role="status">Buscando…</p> : null}
            {results.isError ? <p role="alert">{messageOf(results.error)}</p> : null}
            {results.data && (results.data.items ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">Nenhum alimento encontrado.</p>
            ) : null}
            {results.data ? (
                <ul className="space-y-1">
                    {(results.data.items ?? []).map((food) => (
                        <li key={food.id}>
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() =>
                                    onPick({
                                        id: food.id ?? '',
                                        name: food.name ?? '',
                                        baseUnit: food.baseUnit ?? 'G',
                                    })
                                }
                            >
                                Adicionar {food.name}
                            </Button>
                        </li>
                    ))}
                </ul>
            ) : null}
        </div>
    );
}
