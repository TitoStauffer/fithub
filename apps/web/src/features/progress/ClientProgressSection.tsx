import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { ORIGINS } from '@/features/assessments/assessmentForm';
import { messageOf } from '@/features/clients/messages';
import { useClientApi } from '@/features/clients/useClientApi';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';
import type { BodySeriesPoint } from '@/lib/clientApi';

import type { ChartPoint } from './chartGeometry';
import { LineChart } from './LineChart';
import { formatDay, PERIODS, periodStart, type PeriodWeeks } from './progressPeriod';

const CIRCUMFERENCES: Record<string, string> = {
    neck: 'Pescoço',
    chest: 'Tórax',
    waist: 'Cintura',
    abdomen: 'Abdômen',
    hip: 'Quadril',
    arm: 'Braço',
    forearm: 'Antebraço',
    thigh: 'Coxa',
    calf: 'Panturrilha',
};

const PERCENT = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/** Uma métrica corporal escolhível: o peso, uma circunferência ou o % de gordura de um método. */
interface BodyMetric {
    key: string;
    label: string;
    unit: string;
    points: BodySeriesPoint[];
}

function chartPoints(points: { date?: string; value?: number }[]): ChartPoint[] {
    return points
        .filter((point) => point.value !== undefined)
        .map((point) => ({ label: formatDay(point.date), value: point.value ?? 0 }));
}

/**
 * Evolução do aluno vinculado para o personal (DEV-0094, FR-56): aderência do período, carga e volume do exercício
 * escolhido, recordes e evolução corporal. Cada bloco diz o que falta quando não há dado. O % de gordura vem por
 * método e nunca mistura métodos numa linha (BR-12).
 */
export function ClientProgressSection({ clientId, now = new Date() }: { clientId: string; now?: Date }) {
    const training = useTrainingApi();
    const clients = useClientApi();
    const [weeks, setWeeks] = useState<PeriodWeeks>(4);
    const [exerciseId, setExerciseId] = useState('');
    const [metricKey, setMetricKey] = useState('weight');
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
    const body = useQuery({
        queryKey: ['professional', 'client', clientId, 'body-series'],
        queryFn: () => clients.clientBodySeries(clientId),
        retry: false,
    });

    const adherence = progress.data?.adherence;
    const exercises = (progress.data?.exercises ?? []).filter(
        (exercise) => (exercise.points ?? []).length > 0,
    );
    const exercise = exercises.find((item) => item.exerciseId === exerciseId) ?? exercises[0];
    const metrics: BodyMetric[] = body.data
        ? [
              ...((body.data.weightKg ?? []).length > 0
                  ? [{ key: 'weight', label: 'Peso', unit: 'kg', points: body.data.weightKg ?? [] }]
                  : []),
              ...Object.entries(body.data.circumferences ?? {}).map(([name, points]) => ({
                  key: `circumference:${name}`,
                  label: CIRCUMFERENCES[name] ?? name,
                  unit: 'cm',
                  points,
              })),
              ...(body.data.bodyFat ?? []).map((series) => ({
                  key: `fat:${series.origin ?? ''}`,
                  label: `% de gordura (${ORIGINS[series.origin ?? ''] ?? series.origin ?? ''})`,
                  unit: '%',
                  points: series.points ?? [],
              })),
          ]
        : [];
    const metric = metrics.find((item) => item.key === metricKey) ?? metrics[0];

    return (
        <section aria-label="Evolução" className="space-y-3">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Evolução</h4>
                <label className="flex items-center gap-2 text-sm">
                    Período
                    <select
                        className="rounded border px-2 py-1"
                        value={weeks}
                        onChange={(event) => setWeeks(Number(event.target.value) as PeriodWeeks)}
                    >
                        {PERIODS.map((option) => (
                            <option key={option} value={option}>
                                {option} semanas
                            </option>
                        ))}
                    </select>
                </label>
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

            <div className="space-y-1">
                <h5 className="text-sm font-medium">Evolução corporal</h5>
                {body.isPending ? <p role="status">Carregando a evolução corporal…</p> : null}
                {body.isError ? <p role="alert">{messageOf(body.error)}</p> : null}
                {body.data && metrics.length === 0 ? (
                    <p className="text-muted-foreground">Nenhuma avaliação ainda.</p>
                ) : null}
                {metric ? (
                    <>
                        <label className="flex items-center gap-2 text-sm">
                            Medida
                            <select
                                className="rounded border px-2 py-1"
                                value={metric.key}
                                onChange={(event) => setMetricKey(event.target.value)}
                            >
                                {metrics.map((item) => (
                                    <option key={item.key} value={item.key}>
                                        {item.label}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <LineChart
                            title={metric.label}
                            unit={metric.unit}
                            points={chartPoints(metric.points)}
                        />
                    </>
                ) : null}
            </div>
        </section>
    );
}
