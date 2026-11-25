import {
    fetchMyBodySeries,
    fetchMyDietProgress,
    fetchMyTrainingProgress,
    type BodySeries,
    type BodySeriesPoint,
    type DietProgress,
    type TrainingProgress,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que a tela de evolução precisa da API; injetável nos testes (DEV-0096). */
export interface ProgressApi {
    training(from: string): Promise<TrainingProgress>;
    diet(from: string): Promise<DietProgress>;
    body(): Promise<BodySeries>;
}

export function progressApiFrom(apiFetch: ApiFetch): ProgressApi {
    return {
        training: (from) => fetchMyTrainingProgress(apiFetch, from),
        diet: (from) => fetchMyDietProgress(apiFetch, from),
        body: () => fetchMyBodySeries(apiFetch),
    };
}

/** Os períodos da evolução, em semanas. */
export const PERIODS = [4, 8, 12] as const;
export type PeriodWeeks = (typeof PERIODS)[number];

/** O primeiro dia do período que termina hoje, em aaaa-mm-dd no fuso do aparelho. */
export function periodStart(now: Date, weeks: PeriodWeeks): string {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (weeks * 7 - 1));
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}`;
}

/** Número com uma casa no formato brasileiro, sem Intl (nem todo motor do aparelho traz pt-BR). */
export function formatNumber(value: number): string {
    return String(Math.round(value * 10) / 10).replace('.', ',');
}

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

const FAT_METHODS: Record<string, string> = {
    POLLOCK_3: 'Pollock 3 dobras',
    POLLOCK_7: 'Pollock 7 dobras',
    NAVY: 'Marinha',
    BIOIMPEDANCE: 'Bioimpedância',
};

/** Uma medida do corpo resumida: o primeiro valor, o último e a variação entre eles. */
export interface BodySummary {
    label: string;
    text: string;
}

function summaryText(points: BodySeriesPoint[], unit: string): string {
    const measured = points.filter((point) => point.value !== undefined);
    const first = measured[0]!;
    const last = measured[measured.length - 1]!;
    const value = (point: BodySeriesPoint) => `${formatNumber(point.value ?? 0)} ${unit}`;
    if (measured.length === 1) return `${value(first)} (uma medição)`;
    const delta = Math.round(((last.value ?? 0) - (first.value ?? 0)) * 10) / 10;
    const sign = delta > 0 ? '+' : delta < 0 ? '−' : '';
    return `${value(first)} → ${value(last)} (${sign}${formatNumber(Math.abs(delta))} ${unit})`;
}

/**
 * As medidas do corpo resumidas, na ordem peso, circunferências e % de gordura. Cada método de % de gordura é uma
 * linha própria e a variação é só dentro dele: métodos diferentes não se comparam (BR-12).
 */
export function bodySummaries(series: BodySeries): BodySummary[] {
    const rows: { label: string; unit: string; points: BodySeriesPoint[] }[] = [
        { label: 'Peso', unit: 'kg', points: series.weightKg ?? [] },
        ...Object.entries(series.circumferences ?? {}).map(([name, points]) => ({
            label: CIRCUMFERENCES[name] ?? name,
            unit: 'cm',
            points,
        })),
        ...(series.bodyFat ?? []).map((fat) => ({
            label: `% de gordura (${FAT_METHODS[fat.origin ?? ''] ?? fat.origin ?? ''})`,
            unit: '%',
            points: fat.points ?? [],
        })),
    ];
    return rows
        .filter((row) => row.points.some((point) => point.value !== undefined))
        .map((row) => ({ label: row.label, text: summaryText(row.points, row.unit) }));
}
