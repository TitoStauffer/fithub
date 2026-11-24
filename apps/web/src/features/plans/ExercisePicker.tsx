import { useQuery } from '@tanstack/react-query';
import { useState, type KeyboardEvent } from 'react';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { EXERCISES_KEY, useTrainingApi } from '@/features/exercises/useTrainingApi';

import type { PickedExercise } from './planForm';

/** Busca no catálogo (DEV-0055) para escolher um exercício; mostra até 10 resultados. */
export function ExercisePicker({
    label,
    onPick,
    disabled = false,
}: {
    /** Rótulo do campo de busca, que diz para onde vai o exercício. */
    label: string;
    onPick: (exercise: PickedExercise) => void;
    disabled?: boolean;
}) {
    const api = useTrainingApi();
    const [draft, setDraft] = useState('');
    const [name, setName] = useState<string>();
    const results = useQuery({
        queryKey: [...EXERCISES_KEY, 'picker', name],
        queryFn: () => api.searchExercises({ name, size: 10 }),
        enabled: name !== undefined,
    });

    // Sem <form>: o buscador fica dentro do formulário da ficha, e formulário aninhado não vale em HTML.
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
                    disabled={disabled}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={onKeyDown}
                />
                <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={search}>
                    Buscar
                </Button>
            </div>
            {results.isFetching ? <p role="status">Buscando…</p> : null}
            {results.isError ? <p role="alert">{messageOf(results.error)}</p> : null}
            {results.data && (results.data.items ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">Nenhum exercício encontrado.</p>
            ) : null}
            {results.data ? (
                <ul className="space-y-1">
                    {(results.data.items ?? []).map((exercise) => (
                        <li key={exercise.id}>
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => onPick({ id: exercise.id ?? '', name: exercise.name ?? '' })}
                            >
                                Adicionar {exercise.name}
                            </Button>
                        </li>
                    ))}
                </ul>
            ) : null}
        </div>
    );
}
