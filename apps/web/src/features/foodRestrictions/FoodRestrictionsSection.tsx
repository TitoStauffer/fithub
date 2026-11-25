import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useNutritionApi } from '@/features/foods/useNutritionApi';
import { ApiProblemError } from '@/lib/api';
import type { FoodRestriction, FoodRestrictionKind } from '@/lib/nutritionApi';

import {
    KIND_LABELS,
    KINDS,
    foodRestrictionsKey,
    termsOf,
    validate,
    type RestrictionForm,
} from './restrictionForm';

/**
 * Restrições alimentares e preferências do paciente vinculado (DEV-0086, FR-40): a nutricionista registra, edita e
 * apaga. Alergia e intolerância geram alerta no plano alimentar (DEV-0084); a preferência, não.
 */
export function FoodRestrictionsSection({ clientId }: { clientId: string }) {
    const api = useNutritionApi();
    const restrictions = useQuery({
        queryKey: foodRestrictionsKey(clientId),
        queryFn: () => api.listFoodRestrictions(clientId),
        retry: false,
    });
    const [adding, setAdding] = useState(false);

    return (
        <section aria-label="Restrições alimentares" className="space-y-2">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Restrições alimentares</h4>
                {!adding ? (
                    <Button type="button" size="sm" variant="outline" onClick={() => setAdding(true)}>
                        Adicionar restrição
                    </Button>
                ) : null}
            </div>
            {restrictions.isPending ? <p role="status">Carregando as restrições…</p> : null}
            {restrictions.isError ? <p role="alert">{messageOf(restrictions.error)}</p> : null}
            {restrictions.data && restrictions.data.length === 0 && !adding ? (
                <p className="text-muted-foreground">Nenhuma restrição registrada.</p>
            ) : null}
            {adding ? <RestrictionEditor clientId={clientId} onDone={() => setAdding(false)} /> : null}
            {restrictions.data && restrictions.data.length > 0 ? (
                <ul className="space-y-2">
                    {restrictions.data.map((restriction) => (
                        <li key={restriction.id}>
                            <RestrictionItem clientId={clientId} restriction={restriction} />
                        </li>
                    ))}
                </ul>
            ) : null}
        </section>
    );
}

function RestrictionItem({ clientId, restriction }: { clientId: string; restriction: FoodRestriction }) {
    const api = useNutritionApi();
    const queryClient = useQueryClient();
    const [editing, setEditing] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const remove = useMutation({
        mutationFn: () => api.removeFoodRestriction(clientId, restriction.id ?? ''),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: foodRestrictionsKey(clientId) }),
    });
    const kind = KIND_LABELS[restriction.kind ?? ''] ?? restriction.kind;

    if (editing) {
        return (
            <RestrictionEditor
                clientId={clientId}
                restriction={restriction}
                onDone={() => setEditing(false)}
            />
        );
    }
    return (
        <article aria-label={`${kind}: ${restriction.description}`} className="space-y-1 rounded border p-2">
            <p>
                <span className="font-medium">{kind}</span> — {restriction.description}
            </p>
            <p className="text-sm text-muted-foreground">Termos: {(restriction.terms ?? []).join(', ')}</p>
            <div className="flex flex-wrap items-center gap-2">
                <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(true)}>
                    Editar
                </Button>
                {confirming ? (
                    <>
                        <span className="text-sm">Apagar esta restrição?</span>
                        <Button
                            type="button"
                            size="sm"
                            disabled={remove.isPending}
                            onClick={() => remove.mutate()}
                        >
                            Confirmar exclusão
                        </Button>
                        <Button type="button" size="sm" variant="ghost" onClick={() => setConfirming(false)}>
                            Cancelar
                        </Button>
                    </>
                ) : (
                    <Button type="button" size="sm" variant="ghost" onClick={() => setConfirming(true)}>
                        Apagar
                    </Button>
                )}
            </div>
            {remove.isError ? <p role="alert">{messageOf(remove.error)}</p> : null}
        </article>
    );
}

/** Formulário de nova restrição ou de edição, com a validação da API e o erro de campo dela. */
function RestrictionEditor({
    clientId,
    restriction,
    onDone,
}: {
    clientId: string;
    restriction?: FoodRestriction;
    onDone: () => void;
}) {
    const api = useNutritionApi();
    const queryClient = useQueryClient();
    const [form, setForm] = useState<RestrictionForm>({
        kind: restriction?.kind ?? '',
        description: restriction?.description ?? '',
        terms: (restriction?.terms ?? []).join(', '),
    });
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string>();

    const save = useMutation({
        mutationFn: () => {
            const input = {
                kind: form.kind as FoodRestrictionKind,
                description: form.description.trim(),
                terms: termsOf(form.terms),
            };
            return restriction?.id
                ? api.updateFoodRestriction(clientId, restriction.id, input)
                : api.createFoodRestriction(clientId, input);
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: foodRestrictionsKey(clientId) });
            onDone();
        },
        onError: (error) => {
            if (error instanceof ApiProblemError && Object.keys(error.fieldErrors).length > 0) {
                setErrors(error.fieldErrors);
                return;
            }
            setFormError(messageOf(error));
        },
    });

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (save.isPending) return;
        setFormError(undefined);
        const found = validate(form);
        setErrors(found);
        if (Object.keys(found).length === 0) save.mutate();
    };

    // O erro de um termo vem como `terms[1]`; ele aparece junto do campo dos termos.
    const termErrors = Object.entries(errors).filter(([key]) => key === 'terms' || key.startsWith('terms['));
    const invalid = (invalidNow: boolean, id: string) => ({
        'aria-invalid': invalidNow ? true : undefined,
        'aria-describedby': invalidNow ? id : undefined,
    });

    return (
        <form noValidate onSubmit={submit} className="space-y-2 rounded border p-2">
            <label className="block space-y-1">
                <span className="text-sm font-medium">Tipo</span>
                <select
                    className="block rounded border px-2 py-1"
                    value={form.kind}
                    onChange={(event) =>
                        setForm({ ...form, kind: event.target.value as FoodRestrictionKind | '' })
                    }
                    {...invalid(Boolean(errors.kind), 'kind-erro')}
                >
                    <option value="">Escolha</option>
                    {KINDS.map((kind) => (
                        <option key={kind} value={kind}>
                            {KIND_LABELS[kind]}
                        </option>
                    ))}
                </select>
            </label>
            {errors.kind ? (
                <p id="kind-erro" className="text-sm text-red-700">
                    {errors.kind}
                </p>
            ) : null}
            <label className="block space-y-1">
                <span className="text-sm font-medium">Descrição</span>
                <input
                    className="block w-full rounded border px-2 py-1"
                    value={form.description}
                    onChange={(event) => setForm({ ...form, description: event.target.value })}
                    {...invalid(Boolean(errors.description), 'description-erro')}
                />
            </label>
            {errors.description ? (
                <p id="description-erro" className="text-sm text-red-700">
                    {errors.description}
                </p>
            ) : null}
            <label className="block space-y-1">
                <span className="text-sm font-medium">Termos (separados por vírgula)</span>
                <input
                    className="block w-full rounded border px-2 py-1"
                    placeholder="Ex.: leite, queijo, lactose"
                    value={form.terms}
                    onChange={(event) => setForm({ ...form, terms: event.target.value })}
                    {...invalid(termErrors.length > 0, 'terms-erro')}
                />
            </label>
            <p className="text-xs text-muted-foreground">
                O plano alimentar alerta o alimento que tem um desses termos no nome ou nos ingredientes (só
                alergia e intolerância).
            </p>
            {termErrors.length > 0 ? (
                <div id="terms-erro">
                    {termErrors.map(([key, message]) => (
                        <p key={key} className="text-sm text-red-700">
                            {message}
                        </p>
                    ))}
                </div>
            ) : null}
            {errors.restrictions ? <p className="text-sm text-red-700">{errors.restrictions}</p> : null}
            {formError ? <p role="alert">{formError}</p> : null}
            <div className="flex gap-2">
                <Button type="submit" size="sm" disabled={save.isPending}>
                    {save.isPending ? 'Salvando…' : 'Salvar restrição'}
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={onDone}>
                    Cancelar
                </Button>
            </div>
        </form>
    );
}
