import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';
import { BODY_REGIONS, regionLabel } from '@/features/plans/planForm';
import { ApiProblemError } from '@/lib/api';
import type { BodyRegion, MobilityRestriction } from '@/lib/trainingApi';

const MAX_DESCRIPTION = 500;

const restrictionsKey = (clientId: string) => ['personal', 'client', clientId, 'restrictions'] as const;

interface RestrictionForm {
    region: BodyRegion | '';
    description: string;
}

/** Como a API (DEV-0058): região obrigatória e descrição de 1 a 500 caracteres. */
function validate(form: RestrictionForm): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!form.region) errors.region = 'Informe a região do corpo.';
    const description = form.description.trim();
    if (description.length === 0 || description.length > MAX_DESCRIPTION) {
        errors.description = 'A descrição precisa ter de 1 a 500 caracteres.';
    }
    return errors;
}

/**
 * Restrições de mobilidade do aluno vinculado (DEV-0064, FR-26): o personal registra, edita e remove. A ficha alerta
 * quando um exercício envolve uma dessas regiões (DEV-0062).
 */
export function RestrictionsSection({ clientId }: { clientId: string }) {
    const api = useTrainingApi();
    const restrictions = useQuery({
        queryKey: restrictionsKey(clientId),
        queryFn: () => api.listRestrictions(clientId),
        retry: false,
    });
    const [adding, setAdding] = useState(false);

    return (
        <section aria-label="Restrições de mobilidade" className="space-y-2">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Restrições de mobilidade</h4>
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

function RestrictionItem({ clientId, restriction }: { clientId: string; restriction: MobilityRestriction }) {
    const api = useTrainingApi();
    const queryClient = useQueryClient();
    const [editing, setEditing] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const remove = useMutation({
        mutationFn: () => api.removeRestriction(clientId, restriction.id ?? ''),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: restrictionsKey(clientId) }),
    });
    const label = regionLabel(restriction.region ?? '');

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
        <article aria-label={`Restrição: ${label}`} className="space-y-1 rounded border p-2">
            <p>
                <span className="font-medium capitalize">{label}</span> — {restriction.description}
            </p>
            <div className="flex flex-wrap items-center gap-2">
                <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(true)}>
                    Editar
                </Button>
                {confirming ? (
                    <>
                        <span className="text-sm">Remover esta restrição?</span>
                        <Button
                            type="button"
                            size="sm"
                            disabled={remove.isPending}
                            onClick={() => remove.mutate()}
                        >
                            Confirmar remoção
                        </Button>
                        <Button type="button" size="sm" variant="ghost" onClick={() => setConfirming(false)}>
                            Cancelar
                        </Button>
                    </>
                ) : (
                    <Button type="button" size="sm" variant="ghost" onClick={() => setConfirming(true)}>
                        Remover
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
    restriction?: MobilityRestriction;
    onDone: () => void;
}) {
    const api = useTrainingApi();
    const queryClient = useQueryClient();
    const [form, setForm] = useState<RestrictionForm>({
        region: restriction?.region ?? '',
        description: restriction?.description ?? '',
    });
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string>();

    const save = useMutation({
        mutationFn: () => {
            const input = { region: form.region as BodyRegion, description: form.description.trim() };
            return restriction?.id
                ? api.updateRestriction(clientId, restriction.id, input)
                : api.createRestriction(clientId, input);
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: restrictionsKey(clientId) });
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

    return (
        <form noValidate onSubmit={submit} className="space-y-2 rounded border p-2">
            <label className="block space-y-1">
                <span className="text-sm font-medium">Região do corpo</span>
                <select
                    className="block rounded border px-2 py-1"
                    value={form.region}
                    onChange={(event) => setForm({ ...form, region: event.target.value as BodyRegion | '' })}
                    aria-invalid={errors.region ? true : undefined}
                    aria-describedby={errors.region ? 'region-erro' : undefined}
                >
                    <option value="">Escolha</option>
                    {BODY_REGIONS.map((region) => (
                        <option key={region} value={region}>
                            {regionLabel(region)}
                        </option>
                    ))}
                </select>
            </label>
            {errors.region ? (
                <p id="region-erro" className="text-sm text-red-700">
                    {errors.region}
                </p>
            ) : null}
            <label className="block space-y-1">
                <span className="text-sm font-medium">Descrição</span>
                <textarea
                    className="block w-full rounded border px-2 py-1"
                    rows={2}
                    value={form.description}
                    onChange={(event) => setForm({ ...form, description: event.target.value })}
                    aria-invalid={errors.description ? true : undefined}
                    aria-describedby={errors.description ? 'description-erro' : undefined}
                />
            </label>
            {errors.description ? (
                <p id="description-erro" className="text-sm text-red-700">
                    {errors.description}
                </p>
            ) : null}
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
