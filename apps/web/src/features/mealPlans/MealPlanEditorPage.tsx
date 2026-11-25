import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useNutritionApi } from '@/features/foods/useNutritionApi';
import { ApiProblemError } from '@/lib/api';
import type { MealPlan } from '@/lib/nutritionApi';

import { FoodPicker } from './FoodPicker';
import { MealItemEditor } from './MealItemEditor';
import {
    LIMITS,
    STATUS_LABELS,
    emptyForm,
    emptyItem,
    emptyMeal,
    formOf,
    macrosText,
    mealPlansKey,
    toInput,
    validate,
    validateForPublishing,
    type ItemForm,
    type MealForm,
    type MealPlanForm,
} from './mealPlanForm';

function todayIso(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const mealPlanKey = (planId: string) => ['nutritionist', 'meal-plan', planId] as const;

/**
 * Editor do plano alimentar do paciente vinculado (DEV-0085): `/app/clientes/:clientId/planos/novo` ou
 * `.../planos/:planId`.
 */
export function MealPlanEditorPage({ now = new Date() }: { now?: Date }) {
    const { clientId = '', planId } = useParams();
    const api = useNutritionApi();
    // O aviso mora aqui: o editor é remontado quando o plano muda de versão ou de estado, e o aviso tem de ficar.
    const [notice, setNotice] = useState<string>();
    const plan = useQuery({
        queryKey: mealPlanKey(planId ?? ''),
        queryFn: () => api.mealPlan(planId ?? ''),
        enabled: Boolean(planId),
        retry: false,
    });

    if (!planId) {
        return (
            <MealPlanEditor
                clientId={clientId}
                initial={emptyForm(todayIso(now))}
                notice={notice}
                onNotice={setNotice}
            />
        );
    }
    if (plan.isError) return <AccessEnded clientId={clientId} error={plan.error} />;
    if (plan.isPending) return <p role="status">Carregando o plano…</p>;
    // A chave troca a cada versão: recarregar depois de um conflito remonta o formulário com o que está salvo.
    return (
        <MealPlanEditor
            key={`${plan.data.id}-${plan.data.version}-${plan.data.status}-${plan.data.updatedAt}`}
            clientId={clientId}
            plan={plan.data}
            initial={formOf(plan.data)}
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
                    ? 'O acesso a este plano acabou: o vínculo foi encerrado ou o plano não existe.'
                    : messageOf(error)}
            </p>
            <Link className="underline" to={`/app/clientes/${clientId}`}>
                Voltar para o paciente
            </Link>
        </section>
    );
}

function MealPlanEditor({
    clientId,
    plan,
    initial,
    notice,
    onNotice: setNotice,
}: {
    clientId: string;
    plan?: MealPlan;
    initial: MealPlanForm;
    notice: string | undefined;
    onNotice: (notice: string | undefined) => void;
}) {
    const api = useNutritionApi();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [form, setForm] = useState<MealPlanForm>(initial);
    const [current, setCurrent] = useState<MealPlan | undefined>(plan);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [dirty, setDirty] = useState(false);
    const [confirmingPublish, setConfirmingPublish] = useState(false);
    const [accessEnded, setAccessEnded] = useState<unknown>();

    const published = current?.status === 'PUBLISHED';
    const readOnly = current?.status === 'REPLACED';
    // Os macros e os alertas são da API: com edição pendente, eles não valem mais e somem até salvar.
    const saved = dirty ? undefined : current;

    const handleError = async (error: unknown) => {
        if (error instanceof ApiProblemError && error.status === 404) {
            setAccessEnded(error);
            return;
        }
        if (error instanceof ApiProblemError && error.status === 409 && current?.id) {
            setNotice('O plano mudou desde que você o abriu. Ele foi recarregado com o que está salvo.');
            await queryClient.invalidateQueries({ queryKey: mealPlanKey(current.id) });
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
                ? api.updateMealPlan(current.id, toInput(form, published ? current.version : undefined))
                : api.createMealPlan(clientId, toInput(form)),
        onSuccess: async (result) => {
            setCurrent(result);
            setDirty(false);
            setNotice(published ? `Plano salvo: versão ${result.version} publicada.` : 'Plano salvo.');
            queryClient.setQueryData(mealPlanKey(result.id ?? ''), result);
            await queryClient.invalidateQueries({ queryKey: mealPlansKey(clientId) });
            if (!current?.id)
                void navigate(`/app/clientes/${clientId}/planos/${result.id ?? ''}`, { replace: true });
        },
        onError: handleError,
    });

    const publish = useMutation({
        mutationFn: () => api.publishMealPlan(current?.id ?? ''),
        onSuccess: async (result) => {
            setCurrent(result);
            setConfirmingPublish(false);
            setNotice('Plano publicado: o paciente já pode vê-lo.');
            queryClient.setQueryData(mealPlanKey(result.id ?? ''), result);
            await queryClient.invalidateQueries({ queryKey: mealPlansKey(clientId) });
        },
        onError: async (error) => {
            setConfirmingPublish(false);
            await handleError(error);
        },
    });

    if (accessEnded) return <AccessEnded clientId={clientId} error={accessEnded} />;

    const update = (next: MealPlanForm) => {
        setForm(next);
        setDirty(true);
    };
    const updateMeal = (position: number, meal: MealForm) =>
        update({ ...form, meals: form.meals.map((m, i) => (i === position ? meal : m)) });
    const updateItem = (position: number, index: number, item: ItemForm) => {
        const meal = form.meals[position];
        if (meal)
            updateMeal(position, { ...meal, items: meal.items.map((it, i) => (i === index ? item : it)) });
    };
    const moveItem = (position: number, index: number, offset: -1 | 1) => {
        const meal = form.meals[position];
        if (!meal) return;
        const items = [...meal.items];
        const moved = items[index];
        const other = items[index + offset];
        if (!moved || !other) return;
        items[index] = other;
        items[index + offset] = moved;
        updateMeal(position, { ...meal, items });
    };
    const moveMeal = (position: number, offset: -1 | 1) => {
        const meals = [...form.meals];
        const moved = meals[position];
        const other = meals[position + offset];
        if (!moved || !other) return;
        meals[position] = other;
        meals[position + offset] = moved;
        update({ ...form, meals });
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

    const errorOf = (key: string) =>
        errors[key] ? (
            <p id={`${key}-erro`} className="text-sm text-red-700">
                {errors[key]}
            </p>
        ) : null;
    const invalid = (key: string) => ({
        'aria-invalid': errors[key] ? true : undefined,
        'aria-describedby': errors[key] ? `${key}-erro` : undefined,
    });

    return (
        <section className="max-w-3xl space-y-4">
            <Link className="underline" to={`/app/clientes/${clientId}`}>
                Voltar para o paciente
            </Link>
            <h3 className="text-base font-semibold">
                {current
                    ? `Plano alimentar: ${STATUS_LABELS[current.status ?? ''] ?? ''}, versão ${current.version}`
                    : 'Novo plano alimentar'}
            </h3>
            {published ? (
                <p className="text-sm">
                    Este plano está publicado: salvar cria a versão {(current?.version ?? 0) + 1}, e a versão
                    atual fica guardada sem mudar.
                </p>
            ) : null}
            {readOnly ? (
                <p className="text-sm">Este plano foi substituído por outro e não muda mais.</p>
            ) : null}
            {notice ? <p role="status">{notice}</p> : null}
            <form noValidate onSubmit={submit} className="space-y-4">
                <div className="flex flex-wrap gap-3">
                    <label className="block space-y-1">
                        <span className="text-sm font-medium">Nome do plano</span>
                        <input
                            className="block rounded border px-2 py-1"
                            value={form.name}
                            disabled={readOnly}
                            onChange={(event) => update({ ...form, name: event.target.value })}
                            {...invalid('name')}
                        />
                    </label>
                    <label className="block space-y-1">
                        <span className="text-sm font-medium">Início</span>
                        <input
                            type="date"
                            className="block rounded border px-2 py-1"
                            value={form.startsOn}
                            disabled={readOnly}
                            onChange={(event) => update({ ...form, startsOn: event.target.value })}
                            {...invalid('startsOn')}
                        />
                    </label>
                </div>
                {errorOf('name')}
                {errorOf('startsOn')}
                <label className="block space-y-1">
                    <span className="text-sm font-medium">Observação geral</span>
                    <textarea
                        className="block w-full rounded border px-2 py-1"
                        maxLength={LIMITS.maxNote}
                        value={form.note}
                        disabled={readOnly}
                        onChange={(event) => update({ ...form, note: event.target.value })}
                        {...invalid('note')}
                    />
                </label>
                {errorOf('note')}
                {errorOf('meals')}
                {form.meals.map((meal, position) => {
                    const path = `meals[${position}]`;
                    const number = position + 1;
                    const savedMeal = saved?.meals?.[position];
                    return (
                        <fieldset key={position} className="space-y-2 rounded border p-3">
                            <legend className="font-medium">Refeição {number}</legend>
                            <div className="flex flex-wrap items-center gap-2">
                                <input
                                    aria-label={`Nome da refeição ${number}`}
                                    className="rounded border px-2 py-1"
                                    placeholder="Ex.: Café da manhã"
                                    maxLength={LIMITS.maxMealName}
                                    value={meal.name}
                                    disabled={readOnly}
                                    onChange={(event) =>
                                        updateMeal(position, { ...meal, name: event.target.value })
                                    }
                                    {...invalid(`${path}.name`)}
                                />
                                <input
                                    aria-label={`Horário da refeição ${number}`}
                                    className="w-20 rounded border px-2 py-1"
                                    placeholder="07:30"
                                    value={meal.suggestedTime}
                                    disabled={readOnly}
                                    onChange={(event) =>
                                        updateMeal(position, { ...meal, suggestedTime: event.target.value })
                                    }
                                    {...invalid(`${path}.suggestedTime`)}
                                />
                                {!readOnly ? (
                                    <>
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            disabled={position === 0}
                                            onClick={() => moveMeal(position, -1)}
                                        >
                                            Subir refeição {number}
                                        </Button>
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            disabled={position === form.meals.length - 1}
                                            onClick={() => moveMeal(position, 1)}
                                        >
                                            Descer refeição {number}
                                        </Button>
                                    </>
                                ) : null}
                                {!readOnly && form.meals.length > 1 ? (
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="sm"
                                        onClick={() =>
                                            update({
                                                ...form,
                                                meals: form.meals.filter((_, i) => i !== position),
                                            })
                                        }
                                    >
                                        Remover refeição {number}
                                    </Button>
                                ) : null}
                            </div>
                            {errorOf(`${path}.name`)}
                            {errorOf(`${path}.suggestedTime`)}
                            <input
                                aria-label={`Observação da refeição ${number}`}
                                className="w-full rounded border px-2 py-1"
                                placeholder="Observação (opcional)"
                                maxLength={LIMITS.maxNote}
                                value={meal.note}
                                disabled={readOnly}
                                onChange={(event) =>
                                    updateMeal(position, { ...meal, note: event.target.value })
                                }
                            />
                            {errorOf(`${path}.note`)}
                            {errorOf(`${path}.items`)}
                            {meal.items.map((item, index) => (
                                <MealItemEditor
                                    key={`${item.food.id}-${index}`}
                                    item={item}
                                    path={`${path}.items[${index}]`}
                                    errors={errors}
                                    saved={savedMeal?.items?.[index]}
                                    readOnly={readOnly}
                                    first={index === 0}
                                    last={index === meal.items.length - 1}
                                    onChange={(changed) => updateItem(position, index, changed)}
                                    onMove={(offset) => moveItem(position, index, offset)}
                                    onRemove={() =>
                                        updateMeal(position, {
                                            ...meal,
                                            items: meal.items.filter((_, i) => i !== index),
                                        })
                                    }
                                />
                            ))}
                            {savedMeal ? (
                                <p className="text-sm font-medium">
                                    Total da refeição: {macrosText(savedMeal.total)}
                                </p>
                            ) : null}
                            {!readOnly && meal.items.length < LIMITS.maxItems ? (
                                <FoodPicker
                                    label={`Buscar alimento para a refeição ${number}`}
                                    onPick={(picked) =>
                                        updateMeal(position, {
                                            ...meal,
                                            items: [...meal.items, emptyItem(picked)],
                                        })
                                    }
                                />
                            ) : null}
                        </fieldset>
                    );
                })}
                {!readOnly && form.meals.length < LIMITS.maxMeals ? (
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => update({ ...form, meals: [...form.meals, emptyMeal()] })}
                    >
                        Adicionar refeição
                    </Button>
                ) : null}
                {saved ? (
                    <p className="font-medium">Total do dia: {macrosText(saved.day)}</p>
                ) : current ? (
                    <p className="text-sm text-muted-foreground">
                        Salve para recalcular os macros e os alertas.
                    </p>
                ) : null}
                {!readOnly ? (
                    <div className="flex flex-wrap items-center gap-2">
                        <Button type="submit" disabled={save.isPending}>
                            {save.isPending ? 'Salvando…' : 'Salvar plano'}
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
                        Publicar o plano? O paciente passa a vê-lo, e o plano ativo anterior fica substituído.
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
