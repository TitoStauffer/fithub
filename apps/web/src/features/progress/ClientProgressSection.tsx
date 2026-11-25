import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { messageOf } from '@/features/clients/messages';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';

import { BodyEvolution } from './BodyEvolution';
import { LineChart } from './LineChart';
import { PeriodSelect } from './PeriodSelect';
import { formatDay, periodStart, type PeriodWeeks } from './progressPeriod';

const PERCENT = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/**
 * Evolução do aluno vinculado para o personal (DEV-0094, FR-56): aderência do período, carga e volume do exercício
 * escolhido, recordes e evolução corporal. Cada bloco diz o que falta quando não há dado. O % de gordura vem por
 * método e nunca mistura métodos numa linha (BR-12).
 */
export function ClientProgressSection({ clientId, now = new Date() }: { clientId: string; now?: Date }) {
    const training = useTrainingApi();
    const [weeks, setWeeks] = useState<PeriodWeeks>(4);
    const [exerciseId, setExerciseId] = useState('');
    const from = periodStart(now, weeks);

    const progress = useQuery({
        queryKey: ['personal', 'client', clientId, 'progress', from],
        queryFn: () => training.clientProgress(clientId, from),
        retry: false,
    });
    const records = useQuery({
        queryKey: ['personal', 'client', clientId, 'records'],
        queryFn: () => training.clientRecords(clientId),
        retry: false,
    });

    const adherence = progress.data?.adherence;
    const exercises = (progress.data?.exercises ?? []).filter(
        (exercise) => (exercise.points ?? []).length > 0,
    );
    const exercise = exercises.find((item) => item.exerciseId === exerciseId) ?? exercises[0];

    return (
        <section aria-label="Evolução" className="space-y-3">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Evolução</h4>
                <PeriodSelect weeks={weeks} onChange={setWeeks} />
            </div>

            <div className="space-y-1">
                <h5 className="text-sm font-medium">Aderência</h5>
                {progress.isPending ? <p role="status">Carregando a aderência…</p> : null}
                {progress.isError ? <p role="alert">{messageOf(progress.error)}</p> : null}
                {adherence && adherence.percent === undefined ? (
                    <p className="text-muted-foreground">Nenhuma ficha publicada no período.</p>
                ) : null}
                {adherence && adherence.percent !== undefined ? (
                    <>
                        <p>
                            {adherence.done} de {PERCENT.format(adherence.expected ?? 0)} treinos esperados (
                            {PERCENT.format(adherence.percent)}%)
                        </p>
                        <LineChart
                            title="Treinos finalizados por semana"
                            unit="treinos"
                            points={(adherence.weeks ?? []).map((week) => ({
                                label: `${formatDay(week.start)} a ${formatDay(week.end)}`,
                                value: week.done ?? 0,
                            }))}
                        />
                    </>
                ) : null}
            </div>

            <div className="space-y-1">
                <h5 className="text-sm font-medium">Carga e volume</h5>
                {progress.data && exercises.length === 0 ? (
                    <p className="text-muted-foreground">Nenhum treino finalizado no período.</p>
                ) : null}
                {exercise ? (
                    <>
                        <label className="flex items-center gap-2 text-sm">
                            Exercício
                            <select
                                className="rounded border px-2 py-1"
                                value={exercise.exerciseId}
                                onChange={(event) => setExerciseId(event.target.value)}
                            >
                                {exercises.map((item) => (
                                    <option key={item.exerciseId} value={item.exerciseId}>
                                        {item.name}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <LineChart
                            title={`Maior carga: ${exercise.name ?? ''}`}
                            unit="kg"
                            points={(exercise.points ?? [])
                                .filter((point) => point.maxLoadKg !== undefined)
                                .map((point) => ({
                                    label: formatDay(point.date),
                                    value: point.maxLoadKg ?? 0,
                                }))}
                        />
                        <LineChart
                            title={`Volume: ${exercise.name ?? ''}`}
                            unit="kg"
                            points={(exercise.points ?? []).map((point) => ({
                                label: formatDay(point.date),
                                value: point.volumeKg ?? 0,
                            }))}
                        />
                    </>
                ) : null}
            </div>

            <div className="space-y-1">
                <h5 className="text-sm font-medium">Recordes</h5>
                {records.isPending ? <p role="status">Carregando os recordes…</p> : null}
                {records.isError ? <p role="alert">{messageOf(records.error)}</p> : null}
                {records.data && records.data.length === 0 ? (
                    <p className="text-muted-foreground">Nenhum recorde ainda.</p>
                ) : null}
                {records.data && records.data.length > 0 ? (
                    <table className="text-sm">
                        <caption className="text-left font-medium">Recordes por exercício</caption>
                        <thead>
                            <tr>
                                <th scope="col" className="pr-4 text-left">
                                    Exercício
                                </th>
                                <th scope="col" className="pr-4 text-left">
                                    Maior carga
                                </th>
                                <th scope="col" className="text-left">
                                    1RM estimado
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {records.data.map((record) => (
                                <tr key={record.exerciseId}>
                                    <td className="pr-4">{record.exerciseName}</td>
                                    <td className="pr-4">
                                        {record.maxLoad
                                            ? `${PERCENT.format(record.maxLoad.valueKg ?? 0)} kg × ${record.maxLoad.reps ?? 0} em ${formatDay(record.maxLoad.date)}`
                                            : '—'}
                                    </td>
                                    <td>
                                        {record.estimatedOneRepMax
                                            ? `${PERCENT.format(record.estimatedOneRepMax.valueKg ?? 0)} kg`
                                            : '—'}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                ) : null}
            </div>

            <BodyEvolution clientId={clientId} />
        </section>
    );
}
