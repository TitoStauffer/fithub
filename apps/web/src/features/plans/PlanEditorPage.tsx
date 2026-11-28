import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';
import { ApiProblemError } from '@/lib/api';
import { isSubscriptionExpired } from '@/features/subscription/subscription';
import { SubscriptionExpiredNotice } from '@/features/subscription/SubscriptionExpiredNotice';
import type { WorkoutPlan, WorkoutPlanAlert } from '@/lib/trainingApi';

import { ExercisePicker } from './ExercisePicker';
import { ItemEditor } from './ItemEditor';
import {
    LIMITS,
    emptyForm,
    emptyItem,
    exerciseIdsOf,
    formOf,
    letterOf,
    toInput,
    validate,
    validateForPublishing,
    STATUS_LABELS,
    plansKey,
    type DivisionForm,
    type ItemForm,
    type PlanForm,
} from './planForm';

function todayIso(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const planKey = (planId: string) => ['personal', 'plan', planId] as const;

/**
 * Editor da ficha do aluno vinculado (DEV-0063): `/app/clientes/:clientId/fichas/nova` ou `.../fichas/:planId`. Carrega a
 * ficha e os nomes dos exercícios antes de montar o formulário.
 */
export function PlanEditorPage({ now = new Date() }: { now?: Date }) {
    const { clientId = '', planId } = useParams();
    const api = useTrainingApi();
    // O aviso mora aqui: o editor é remontado quando a ficha muda de versão ou de estado, e o aviso tem de ficar.
    const [notice, setNotice] = useState<string>();
    const plan = useQuery({
        queryKey: planKey(planId ?? ''),
        queryFn: () => api.plan(planId ?? ''),
        enabled: Boolean(planId),
        retry: false,
    });
    const ids = plan.data ? exerciseIdsOf(plan.data) : [];
    const names = useQueries({
        queries: ids.map((id) => ({
            queryKey: ['personal', 'exercises', 'one', id],
            queryFn: () => api.exercise(id),
            staleTime: Infinity,
        })),
    });

    if (!planId) {
        return (
            <PlanEditor
                clientId={clientId}
                initial={emptyForm(todayIso(now))}
                notice={notice}
                onNotice={setNotice}
            />
        );
    }
    if (plan.isError) return <AccessEnded clientId={clientId} error={plan.error} />;
    if (plan.isPending || names.some((name) => name.isPending))
        return <p role="status">Carregando a ficha…</p>;

    const nameById = new Map<string, string>();
    names.forEach((name, index) => {
        const id = ids[index];
        if (id) nameById.set(id, name.data?.name ?? 'Exercício indisponível');
    });
    // A chave troca a cada versão: recarregar depois de um conflito remonta o formulário com o que está salvo.
    return (
        <PlanEditor
            key={`${plan.data.id}-${plan.data.version}-${plan.data.status}`}
            clientId={clientId}
            plan={plan.data}
            initial={formOf(plan.data, nameById)}
            notice={notice}
            onNotice={setNotice}
        />
    );
}

function AccessEnded({ clientId, error }: { clientId: string; error: unknown }) {
    const ended = error instanceof ApiProblemError && error.status === 404;
    return (
        <section className="space-y-3">
            <p role="alert">
                {ended
                    ? 'O acesso a esta ficha acabou: o vínculo foi encerrado ou a ficha não existe.'
                    : messageOf(error)}
            </p>
            <Link className="underline" to={`/app/clientes/${clientId}`}>
                Voltar para o aluno
            </Link>
        </section>
    );
}

function PlanEditor({
    clientId,
    plan,
    initial,
    notice,
    onNotice: setNotice,
}: {
    clientId: string;
    plan?: WorkoutPlan;
    initial: PlanForm;
    notice: string | undefined;
    onNotice: (notice: string | undefined) => void;
}) {
    const api = useTrainingApi();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [form, setForm] = useState<PlanForm>(initial);
    const [current, setCurrent] = useState<WorkoutPlan | undefined>(plan);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [dirty, setDirty] = useState(false);
    const [confirmingPublish, setConfirmingPublish] = useState(false);
    const [accessEnded, setAccessEnded] = useState<unknown>();
    const [expired, setExpired] = useState(false);

    const published = current?.status === 'PUBLISHED';
    const readOnly = current?.status === 'REPLACED';
    const alerts: WorkoutPlanAlert[] = current?.alerts ?? [];

    const handleError = async (error: unknown) => {
        // DEV-0102: sem assinatura válida, nada se cria, edita ou publica (BR-14).
        if (isSubscriptionExpired(error)) {
            setExpired(true);
            return;
        }
        if (error instanceof ApiProblemError && error.status === 404) {
            setAccessEnded(error);
            return;
        }
        if (error instanceof ApiProblemError && error.status === 409 && current?.id) {
            setNotice('A ficha mudou desde que você a abriu. Ela foi recarregada com o que está salvo.');
            await queryClient.invalidateQueries({ queryKey: planKey(current.id) });
            return;
        }
        if (error instanceof ApiProblemError && Object.keys(error.fieldErrors).length > 0) {
            setErrors(error.fieldErrors);
            return;
        }
        setNotice(messageOf(error));
    };

    const save = useMutation({
        mutationFn: () =>
            current?.id
                ? api.updatePlan(current.id, toInput(form, published ? current.version : undefined))
                : api.createPlan(clientId, toInput(form)),
        onSuccess: async (saved) => {
            setCurrent(saved);
            setDirty(false);
            setNotice(published ? `Ficha salva: versão ${saved.version} publicada.` : 'Ficha salva.');
            queryClient.setQueryData(planKey(saved.id ?? ''), saved);
            await queryClient.invalidateQueries({ queryKey: plansKey(clientId) });
            if (!current?.id)
                void navigate(`/app/clientes/${clientId}/fichas/${saved.id ?? ''}`, { replace: true });
        },
        onError: handleError,
    });

    const publish = useMutation({
        mutationFn: () => api.publishPlan(current?.id ?? ''),
        onSuccess: async (saved) => {
            setCurrent(saved);
            setConfirmingPublish(false);
            setNotice('Ficha publicada: o aluno já vê no app.');
            queryClient.setQueryData(planKey(saved.id ?? ''), saved);
            await queryClient.invalidateQueries({ queryKey: plansKey(clientId) });
        },
        onError: async (error) => {
            setConfirmingPublish(false);
            await handleError(error);
        },
    });

    if (accessEnded) return <AccessEnded clientId={clientId} error={accessEnded} />;

    const update = (next: PlanForm) => {
        setForm(next);
        setDirty(true);
    };
    const updateDivision = (position: number, division: DivisionForm) =>
        update({ ...form, divisions: form.divisions.map((d, i) => (i === position ? division : d)) });
    const updateItem = (position: number, index: number, item: ItemForm) => {
        const division = form.divisions[position];
        if (division)
            updateDivision(position, {
                ...division,
                items: division.items.map((it, i) => (i === index ? item : it)),
            });
    };
    const moveItem = (position: number, index: number, offset: -1 | 1) => {
        const division = form.divisions[position];
        if (!division) return;
        const items = [...division.items];
        const target = index + offset;
        const moved = items[index];
        const other = items[target];
        if (!moved || !other) return;
        items[index] = other;
        items[target] = moved;
        updateDivision(position, { ...division, items });
    };

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (save.isPending || readOnly) return;
        setNotice(undefined);
        const found = validate(form);
        setErrors(found);
        if (Object.keys(found).length === 0) save.mutate();
    };

    const startPublishing = () => {
        const found = validateForPublishing(form);
        setErrors(found);
        if (Object.keys(found).length === 0) setConfirmingPublish(true);
    };

    const headerField = (key: 'name' | 'startsOn' | 'validityWeeks', label: string, type = 'text') => (
        <label className="block space-y-1">
            <span className="text-sm font-medium">{label}</span>
            <input
                type={type}
                className="block rounded border px-2 py-1"
                value={form[key]}
                disabled={readOnly}
                onChange={(event) => update({ ...form, [key]: event.target.value })}
                aria-invalid={errors[key] ? true : undefined}
                aria-describedby={errors[key] ? `${key}-erro` : undefined}
            />
        </label>
    );
    const errorOf = (key: string) =>
        errors[key] ? (
            <p id={`${key}-erro`} className="text-sm text-red-700">
                {errors[key]}
            </p>
        ) : null;

    return (
        <section className="max-w-3xl space-y-4">
            <Link className="underline" to={`/app/clientes/${clientId}`}>
                Voltar para o aluno
            </Link>
            <h3 className="text-base font-semibold">
                {current
                    ? `Ficha: ${STATUS_LABELS[current.status ?? ''] ?? ''}, versão ${current.version}`
                    : 'Nova ficha'}
            </h3>
            {published ? (
                <p className="text-sm">
                    Esta ficha está publicada: salvar cria a versão {(current?.version ?? 0) + 1}, e os
                    treinos já registrados continuam na versão anterior.
                </p>
            ) : null}
            {readOnly ? (
                <p className="text-sm">Esta ficha foi substituída por outra e não muda mais.</p>
            ) : null}
            {expired ? <SubscriptionExpiredNotice /> : null}
            {notice ? <p role="status">{notice}</p> : null}
            <form noValidate onSubmit={submit} className="space-y-4">
                <div className="flex flex-wrap gap-3">
                    {headerField('name', 'Nome da ficha')}
                    {headerField('startsOn', 'Início', 'date')}
                    {headerField('validityWeeks', 'Validade (semanas)')}
                </div>
                {errorOf('name')}
                {errorOf('startsOn')}
                {errorOf('validityWeeks')}
                {errorOf('divisions')}
                {form.divisions.map((division, position) => {
                    const letter = letterOf(position);
                    const path = `divisions[${position}]`;
                    return (
                        <fieldset key={position} className="space-y-2 rounded border p-3">
                            <legend className="font-medium">Treino {letter}</legend>
                            <div className="flex flex-wrap items-center gap-2">
                                <input
                                    aria-label={`Nome da divisão ${letter}`}
                                    className="rounded border px-2 py-1"
                                    placeholder="Ex.: Peito e tríceps"
                                    maxLength={LIMITS.maxDivisionName}
                                    value={division.name}
                                    disabled={readOnly}
                                    onChange={(event) =>
                                        updateDivision(position, { ...division, name: event.target.value })
                                    }
                                />
                                {!readOnly && form.divisions.length > 1 ? (
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="sm"
                                        onClick={() =>
                                            update({
                                                ...form,
                                                divisions: form.divisions.filter((_, i) => i !== position),
                                            })
                                        }
                                    >
                                        Remover treino {letter}
                                    </Button>
                                ) : null}
                            </div>
                            {errorOf(`${path}.name`)}
                            {errorOf(`${path}.items`)}
                            {division.items.map((item, index) => (
                                <ItemEditor
                                    key={`${item.exercise.id}-${index}`}
                                    item={item}
                                    path={`${path}.items[${index}]`}
                                    errors={errors}
                                    alerts={
                                        dirty
                                            ? []
                                            : alerts.filter(
                                                  (alert) =>
                                                      alert.divisionPosition === position &&
                                                      alert.itemPosition === index,
                                              )
                                    }
                                    readOnly={readOnly}
                                    first={index === 0}
                                    last={index === division.items.length - 1}
                                    onChange={(changed) => updateItem(position, index, changed)}
                                    onMove={(offset) => moveItem(position, index, offset)}
                                    onRemove={() =>
                                        updateDivision(position, {
                                            ...division,
                                            items: division.items.filter((_, i) => i !== index),
                                        })
                                    }
                                />
                            ))}
                            {!readOnly && division.items.length < LIMITS.maxItems ? (
                                <ExercisePicker
                                    label={`Buscar exercício para o treino ${letter}`}
                                    onPick={(picked) =>
                                        updateDivision(position, {
                                            ...division,
                                            items: [...division.items, emptyItem(picked)],
                                        })
                                    }
                                />
                            ) : null}
                        </fieldset>
                    );
                })}
                {!readOnly && form.divisions.length < LIMITS.maxDivisions ? (
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() =>
                            update({ ...form, divisions: [...form.divisions, { name: '', items: [] }] })
                        }
                    >
                        Adicionar treino {letterOf(form.divisions.length)}
                    </Button>
                ) : null}
                {!readOnly ? (
                    <div className="flex flex-wrap items-center gap-2">
                        <Button type="submit" disabled={save.isPending}>
                            {save.isPending ? 'Salvando…' : 'Salvar ficha'}
                        </Button>
                        {current?.status === 'DRAFT' && !confirmingPublish ? (
                            <Button
                                type="button"
                                variant="outline"
                                disabled={dirty}
                                onClick={startPublishing}
                            >
                                Publicar
                            </Button>
                        ) : null}
                        {current?.status === 'DRAFT' && dirty ? (
                            <span className="text-sm text-muted-foreground">Salve antes de publicar.</span>
                        ) : null}
                    </div>
                ) : null}
            </form>
            {confirmingPublish ? (
                <div role="group" aria-label="Confirmar publicação" className="space-y-2">
                    <p>
                        Publicar a ficha? O aluno passa a ver no app, e a ficha ativa anterior fica
                        substituída.
                    </p>
                    <Button type="button" disabled={publish.isPending} onClick={() => publish.mutate()}>
                        Confirmar publicação
                    </Button>
                    <Button type="button" variant="outline" onClick={() => setConfirmingPublish(false)}>
                        Cancelar
                    </Button>
                </div>
            ) : null}
        </section>
    );
}
