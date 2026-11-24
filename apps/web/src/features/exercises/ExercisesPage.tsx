import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import type { Equipment, Exercise, ExerciseSearch, MuscleGroup } from '@/lib/trainingApi';

import { EQUIPMENT, MUSCLE_GROUPS, equipmentLabel, muscleLabel } from './exerciseForm';
import { EXERCISES_KEY, useTrainingApi } from './useTrainingApi';

const PAGE_SIZE = 20;

/**
 * Catálogo de exercícios do personal (DEV-0057, FR-22 a FR-24): busca por nome com filtros de grupo muscular e
 * equipamento, em páginas. Cada exercício mostra a mídia por link e, se importado, a atribuição (ADR-010); o
 * próprio pode ser editado e arquivado.
 */
export function ExercisesPage() {
    const api = useTrainingApi();
    const [draft, setDraft] = useState<ExerciseSearch>({ name: '', muscleGroup: '', equipment: '' });
    const [search, setSearch] = useState<ExerciseSearch>({
        name: '',
        muscleGroup: '',
        equipment: '',
        page: 0,
    });

    const results = useQuery({
        queryKey: [...EXERCISES_KEY, search],
        queryFn: () => api.searchExercises({ ...search, size: PAGE_SIZE }),
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
                <h3 className="text-base font-semibold">Exercícios</h3>
                <Button asChild size="sm">
                    <Link to="/app/exercicios/novo">Novo exercício</Link>
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
                    <span className="block text-sm font-medium">Grupo muscular</span>
                    <select
                        className="rounded border px-2 py-1"
                        value={draft.muscleGroup}
                        onChange={(event) =>
                            setDraft({ ...draft, muscleGroup: event.target.value as MuscleGroup | '' })
                        }
                    >
                        <option value="">Todos</option>
                        {MUSCLE_GROUPS.map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </label>
                <label className="space-y-1">
                    <span className="block text-sm font-medium">Equipamento</span>
                    <select
                        className="rounded border px-2 py-1"
                        value={draft.equipment}
                        onChange={(event) =>
                            setDraft({ ...draft, equipment: event.target.value as Equipment | '' })
                        }
                    >
                        <option value="">Todos</option>
                        {EQUIPMENT.map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </label>
                <Button type="submit" variant="outline">
                    Buscar
                </Button>
            </form>

            {results.isPending ? (
                <p role="status">Carregando exercícios…</p>
            ) : results.isError ? (
                <div role="alert" className="space-y-2">
                    <p>{messageOf(results.error)}</p>
                    <Button variant="outline" size="sm" onClick={() => void results.refetch()}>
                        Tentar de novo
                    </Button>
                </div>
            ) : (results.data.items ?? []).length === 0 ? (
                <p>Nenhum exercício encontrado com esses filtros.</p>
            ) : (
                <>
                    <p className="text-sm text-muted-foreground">
                        {total === 1 ? '1 exercício' : `${total} exercícios`}
                    </p>
                    <ul className="space-y-3">
                        {(results.data.items ?? []).map((exercise) => (
                            <li key={exercise.id}>
                                <ExerciseCard exercise={exercise} />
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

function ExerciseCard({ exercise }: { exercise: Exercise }) {
    const own = exercise.kind === 'OWN';
    return (
        <article aria-label={exercise.name} className="space-y-2 rounded border p-3">
            <header className="flex items-center gap-2">
                <h4 className="font-medium">{exercise.name}</h4>
                <span className="rounded bg-muted px-2 text-xs">{own ? 'Próprio' : 'Catálogo'}</span>
            </header>
            <p className="text-sm">
                {(exercise.muscleGroups ?? []).map(muscleLabel).join(', ')} ·{' '}
                {equipmentLabel(exercise.equipment ?? '')}
            </p>
            {exercise.instructions ? (
                <p className="whitespace-pre-line text-sm">{exercise.instructions}</p>
            ) : null}
            {exercise.mediaUrl ? (
                // Link, sem player embutido no MVP; abre em outra aba sem dar acesso a esta página.
                <a
                    className="text-sm underline"
                    href={exercise.mediaUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    Ver vídeo ou imagem
                </a>
            ) : null}
            {exercise.attribution ? (
                <p className="text-xs text-muted-foreground">
                    Fonte: {exercise.attribution.source} · Licença {exercise.attribution.license} · Autor:{' '}
                    {exercise.attribution.author}
                    {exercise.attribution.mediaLicense ? (
                        <>
                            {' '}
                            · Mídia: licença {exercise.attribution.mediaLicense}, autor{' '}
                            {exercise.attribution.mediaAuthor}
                        </>
                    ) : null}
                </p>
            ) : null}
            {own ? <OwnActions exercise={exercise} /> : null}
        </article>
    );
}

/** Editar e arquivar o exercício próprio; arquivar pede confirmação. */
function OwnActions({ exercise }: { exercise: Exercise }) {
    const api = useTrainingApi();
    const queryClient = useQueryClient();
    const [confirming, setConfirming] = useState(false);
    const archive = useMutation({
        mutationFn: () => api.archiveExercise(exercise.id ?? ''),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: EXERCISES_KEY }),
    });

    return (
        <div className="flex flex-wrap items-center gap-2">
            <Button asChild variant="outline" size="sm">
                <Link to={`/app/exercicios/${exercise.id ?? ''}/editar`}>Editar</Link>
            </Button>
            {confirming ? (
                <>
                    <span className="text-sm">
                        Arquivar? Ele sai da busca, mas as fichas que já o usam continuam mostrando.
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
