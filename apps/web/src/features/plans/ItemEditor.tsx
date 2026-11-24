import { Button } from '@/components/ui/button';
import type { WorkoutPlanAlert } from '@/lib/trainingApi';

import { ExercisePicker } from './ExercisePicker';
import { LIMITS, TECHNIQUES, regionLabel, type ItemForm } from './planForm';

/** Um exercício da divisão: prescrição, alternativas, ordem e os alertas de restrição (DEV-0063). */
export function ItemEditor({
    item,
    path,
    errors,
    alerts,
    readOnly,
    first,
    last,
    onChange,
    onMove,
    onRemove,
}: {
    item: ItemForm;
    /** Prefixo das chaves de erro da API, como `divisions[0].items[1]`. */
    path: string;
    errors: Record<string, string>;
    alerts: WorkoutPlanAlert[];
    readOnly: boolean;
    first: boolean;
    last: boolean;
    onChange: (item: ItemForm) => void;
    onMove: (offset: -1 | 1) => void;
    onRemove: () => void;
}) {
    const name = item.exercise.name;
    const set = <K extends keyof ItemForm>(field: K, value: ItemForm[K]) =>
        onChange({ ...item, [field]: value });

    const field = (key: 'sets' | 'repsMin' | 'repsMax' | 'loadKg' | 'restSeconds', label: string) => {
        const errorKey = `${path}.${key}`;
        return (
            <label className="space-y-1 text-sm">
                <span className="block">{label}</span>
                <input
                    aria-label={`${label} de ${name}`}
                    inputMode="decimal"
                    className="w-24 rounded border px-2 py-1"
                    value={item[key]}
                    disabled={readOnly}
                    onChange={(event) => set(key, event.target.value)}
                    aria-invalid={errors[errorKey] ? true : undefined}
                    aria-describedby={errors[errorKey] ? `${errorKey}-erro` : undefined}
                />
            </label>
        );
    };
    const errorOf = (key: string) =>
        errors[`${path}.${key}`] ? (
            <p id={`${path}.${key}-erro`} className="text-sm text-red-700">
                {errors[`${path}.${key}`]}
            </p>
        ) : null;

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
            {alerts.map((alert) => (
                // Alerta, não bloqueio (BR-11): o personal decide manter o exercício.
                <p
                    key={`${alert.restrictionId}-${alert.exerciseId}`}
                    role="note"
                    className="text-sm text-amber-800"
                >
                    Atenção: {alert.alternative ? 'a alternativa' : 'o exercício'} envolve{' '}
                    {regionLabel(alert.region ?? '')}, região restrita do aluno — {alert.description}
                </p>
            ))}
            <div className="flex flex-wrap gap-3">
                {field('sets', 'Séries')}
                {field('repsMin', 'Repetições')}
                {field('repsMax', 'Até (faixa)')}
                {field('loadKg', 'Carga (kg)')}
                {field('restSeconds', 'Descanso (s)')}
                <label className="space-y-1 text-sm">
                    <span className="block">Técnica</span>
                    <select
                        aria-label={`Técnica de ${name}`}
                        className="rounded border px-2 py-1"
                        value={item.technique}
                        disabled={readOnly}
                        onChange={(event) => set('technique', event.target.value as ItemForm['technique'])}
                    >
                        <option value="">Nenhuma</option>
                        {TECHNIQUES.map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </label>
            </div>
            {errorOf('sets')}
            {errorOf('repsMin')}
            {errorOf('repsMax')}
            {errorOf('loadKg')}
            {errorOf('restSeconds')}
            {errorOf('exerciseId')}
            <label className="block space-y-1 text-sm">
                <span className="block">Observação</span>
                <input
                    aria-label={`Observação de ${name}`}
                    className="w-full rounded border px-2 py-1"
                    maxLength={LIMITS.maxNote}
                    value={item.note}
                    disabled={readOnly}
                    onChange={(event) => set('note', event.target.value)}
                />
            </label>
            {errorOf('note')}
            <div className="space-y-1 text-sm">
                <p>Alternativas{item.alternatives.length === 0 ? ': nenhuma' : ''}</p>
                <ul>
                    {item.alternatives.map((alternative, index) => (
                        <li key={alternative.id} className="flex items-center gap-2">
                            {alternative.name}
                            {!readOnly ? (
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    aria-label={`Remover a alternativa ${alternative.name}`}
                                    onClick={() =>
                                        set(
                                            'alternatives',
                                            item.alternatives.filter((_, position) => position !== index),
                                        )
                                    }
                                >
                                    Remover
                                </Button>
                            ) : null}
                            {errorOf(`alternatives[${index}]`)}
                        </li>
                    ))}
                </ul>
                {!readOnly && item.alternatives.length < LIMITS.maxAlternatives ? (
                    <ExercisePicker
                        label={`Buscar alternativa para ${name}`}
                        onPick={(picked) => {
                            if (
                                picked.id === item.exercise.id ||
                                item.alternatives.some((a) => a.id === picked.id)
                            )
                                return;
                            set('alternatives', [...item.alternatives, picked]);
                        }}
                    />
                ) : null}
            </div>
        </article>
    );
}
