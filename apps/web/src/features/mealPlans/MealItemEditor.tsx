import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { useNutritionApi } from '@/features/foods/useNutritionApi';
import type { MealPlan } from '@/lib/nutritionApi';

import { FoodPicker } from './FoodPicker';
import { LIMITS, differenceText, macrosText, unitLabel, type ItemForm } from './mealPlanForm';

type SavedItem = NonNullable<NonNullable<MealPlan['meals']>[number]['items']>[number];

/**
 * Um alimento da refeição (DEV-0085): quantidade ou medida caseira, até 2 alternativas, ordem, e os macros e alertas do
 * plano salvo. Com edição pendente, os macros e os alertas somem até salvar: eles são calculados pela API.
 */
export function MealItemEditor({
    item,
    path,
    errors,
    saved,
    readOnly,
    first,
    last,
    onChange,
    onMove,
    onRemove,
}: {
    item: ItemForm;
    /** Prefixo das chaves de erro da API, como `meals[0].items[1]`. */
    path: string;
    errors: Record<string, string>;
    /** O item como a API devolveu, com os macros e os alertas; ausente quando há edição pendente. */
    saved: SavedItem | undefined;
    readOnly: boolean;
    first: boolean;
    last: boolean;
    onChange: (item: ItemForm) => void;
    onMove: (offset: -1 | 1) => void;
    onRemove: () => void;
}) {
    const api = useNutritionApi();
    const name = item.food.name;
    const unit = unitLabel(item.food.baseUnit);
    const [choosingMeasure, setChoosingMeasure] = useState(item.measure !== null);
    const food = useQuery({
        queryKey: ['nutritionist', 'foods', 'one', item.food.id],
        queryFn: () => api.food(item.food.id),
        enabled: choosingMeasure && !readOnly,
        staleTime: Infinity,
    });
    const set = <K extends keyof ItemForm>(field: K, value: ItemForm[K]) =>
        onChange({ ...item, [field]: value });
    const errorOf = (key: string) =>
        errors[`${path}.${key}`] ? (
            <p id={`${path}.${key}-erro`} className="text-sm text-red-700">
                {errors[`${path}.${key}`]}
            </p>
        ) : null;
    const invalid = (key: string) => ({
        'aria-invalid': errors[`${path}.${key}`] ? true : undefined,
        'aria-describedby': errors[`${path}.${key}`] ? `${path}.${key}-erro` : undefined,
    });

    return (
        <article aria-label={name} className="space-y-2 rounded border p-2">
            <header className="flex flex-wrap items-center gap-2">
                <h6 className="font-medium">{name}</h6>
                {!readOnly ? (
                    <>
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={first}
                            onClick={() => onMove(-1)}
                        >
                            Subir
                        </Button>
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={last}
                            onClick={() => onMove(1)}
                        >
                            Descer
                        </Button>
                        <Button type="button" variant="ghost" size="sm" onClick={onRemove}>
                            Remover
                        </Button>
                    </>
                ) : null}
            </header>
            {(saved?.alerts ?? []).map((alert) => (
                // Alerta, não bloqueio (BR-11): a nutricionista decide manter o alimento.
                <p key={alert.restrictionId} role="note" className="text-sm text-amber-800">
                    Atenção: o alimento conflita com uma restrição do paciente — {alert.description}
                </p>
            ))}
            <div className="flex flex-wrap items-end gap-3">
                {item.measure === null ? (
                    <label className="space-y-1 text-sm">
                        <span className="block">Quantidade ({unit})</span>
                        <input
                            aria-label={`Quantidade de ${name}`}
                            inputMode="decimal"
                            className="w-24 rounded border px-2 py-1"
                            value={item.amount}
                            disabled={readOnly}
                            onChange={(event) => set('amount', event.target.value)}
                            {...invalid('amount')}
                        />
                    </label>
                ) : (
                    <label className="space-y-1 text-sm">
                        <span className="block">Quantas ({item.measure.name})</span>
                        <input
                            aria-label={`Quantas medidas de ${name}`}
                            inputMode="decimal"
                            className="w-24 rounded border px-2 py-1"
                            value={item.measureCount}
                            disabled={readOnly}
                            onChange={(event) => set('measureCount', event.target.value)}
                            {...invalid('measureCount')}
                        />
                    </label>
                )}
                {!readOnly && !choosingMeasure ? (
                    <Button type="button" variant="ghost" size="sm" onClick={() => setChoosingMeasure(true)}>
                        Usar medida caseira
                    </Button>
                ) : null}
                {!readOnly && choosingMeasure ? (
                    <label className="space-y-1 text-sm">
                        <span className="block">Medida caseira</span>
                        <select
                            aria-label={`Medida caseira de ${name}`}
                            className="rounded border px-2 py-1"
                            value={item.measure?.id ?? ''}
                            onChange={(event) => {
                                const picked = (food.data?.measures ?? []).find(
                                    (m) => m.id === event.target.value,
                                );
                                onChange({
                                    ...item,
                                    measure: picked ? { id: picked.id ?? '', name: picked.name ?? '' } : null,
                                    measureCount: picked ? item.measureCount || '1' : '',
                                });
                            }}
                        >
                            <option value="">Sem medida (em {unit})</option>
                            {item.measure && !food.data ? (
                                <option value={item.measure.id}>{item.measure.name}</option>
                            ) : null}
                            {(food.data?.measures ?? []).map((measure) => (
                                <option key={measure.id} value={measure.id}>
                                    {measure.name} ({measure.amount} {unit})
                                </option>
                            ))}
                        </select>
                    </label>
                ) : null}
                {saved ? (
                    <p className="text-sm text-muted-foreground">
                        {saved.amount} {unit}: {macrosText(saved.nutrients)}
                    </p>
                ) : null}
            </div>
            {errorOf('amount')}
            {errorOf('measureCount')}
            {errorOf('measureId')}
            {errorOf('foodId')}
            <div className="space-y-1 text-sm">
                <p>Alternativas{item.alternatives.length === 0 ? ': nenhuma' : ''}</p>
                {errorOf('alternatives')}
                <ul className="space-y-1">
                    {item.alternatives.map((alternative, index) => {
                        const savedAlternative = saved?.alternatives?.[index];
                        return (
                            <li key={alternative.food.id} className="space-y-1">
                                <div className="flex flex-wrap items-center gap-2">
                                    <span>{alternative.food.name}</span>
                                    <input
                                        aria-label={`Quantidade da alternativa ${alternative.food.name}`}
                                        inputMode="decimal"
                                        className="w-24 rounded border px-2 py-1"
                                        value={alternative.amount}
                                        disabled={readOnly}
                                        onChange={(event) =>
                                            set(
                                                'alternatives',
                                                item.alternatives.map((a, i) =>
                                                    i === index ? { ...a, amount: event.target.value } : a,
                                                ),
                                            )
                                        }
                                        {...invalid(`alternatives[${index}].amount`)}
                                    />
                                    <span>{unitLabel(alternative.food.baseUnit)}</span>
                                    {!readOnly ? (
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            aria-label={`Remover a alternativa ${alternative.food.name}`}
                                            onClick={() =>
                                                set(
                                                    'alternatives',
                                                    item.alternatives.filter(
                                                        (_, position) => position !== index,
                                                    ),
                                                )
                                            }
                                        >
                                            Remover
                                        </Button>
                                    ) : null}
                                </div>
                                {savedAlternative ? (
                                    <p className="text-muted-foreground">
                                        Diferença para o principal:{' '}
                                        {differenceText(savedAlternative.difference)}
                                    </p>
                                ) : null}
                                {(savedAlternative?.alerts ?? []).map((alert) => (
                                    <p key={alert.restrictionId} role="note" className="text-amber-800">
                                        Atenção: a alternativa conflita com uma restrição do paciente —{' '}
                                        {alert.description}
                                    </p>
                                ))}
                                {errorOf(`alternatives[${index}].amount`)}
                                {errorOf(`alternatives[${index}].foodId`)}
                            </li>
                        );
                    })}
                </ul>
                {!readOnly && item.alternatives.length < LIMITS.maxAlternatives ? (
                    <FoodPicker
                        label={`Buscar alternativa para ${name}`}
                        onPick={(picked) => {
                            if (
                                picked.id === item.food.id ||
                                item.alternatives.some((a) => a.food.id === picked.id)
                            )
                                return;
                            set('alternatives', [...item.alternatives, { food: picked, amount: '100' }]);
                        }}
                    />
                ) : null}
            </div>
        </article>
    );
}
