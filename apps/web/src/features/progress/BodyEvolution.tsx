import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { ORIGINS } from '@/features/assessments/assessmentForm';
import { messageOf } from '@/features/clients/messages';
import { useClientApi } from '@/features/clients/useClientApi';
import type { BodySeriesPoint } from '@/lib/clientApi';

import type { ChartPoint } from './chartGeometry';
import { LineChart } from './LineChart';
import { formatDay } from './progressPeriod';

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
 * Evolução corporal do cliente vinculado, de todo o histórico (DEV-0093): peso, cada circunferência e o % de gordura
 * de cada método, um por vez. O % de gordura nunca mistura métodos numa linha (BR-12). Usada nos painéis do personal
 * (DEV-0094) e da nutricionista (DEV-0095).
 */
export function BodyEvolution({ clientId }: { clientId: string }) {
    const clients = useClientApi();
    const [metricKey, setMetricKey] = useState('weight');
    const body = useQuery({
        queryKey: ['professional', 'client', clientId, 'body-series'],
        queryFn: () => clients.clientBodySeries(clientId),
        retry: false,
    });

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
                    <LineChart title={metric.label} unit={metric.unit} points={chartPoints(metric.points)} />
                </>
            ) : null}
        </div>
    );
}
