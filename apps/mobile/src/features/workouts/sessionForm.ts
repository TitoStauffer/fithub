import type { RecordedSet, SessionItem } from '../../lib/api';

/** Valores da série como a tela mostra: texto, para o aluno poder ajustar. */
export interface SetDraft {
    load: string;
    reps: string;
}

/** Carga com vírgula, como o aluno digita: 42.5 vira "42,5". */
export function loadText(value: number | null | undefined): string {
    return String(value ?? 0).replace('.', ',');
}

/** "42,5" ou "42.5" em número; vazio ou inválido, `undefined`. */
export function parseLoad(text: string): number | undefined {
    const value = Number(text.trim().replace(',', '.'));
    return text.trim() === '' || Number.isNaN(value) ? undefined : value;
}

export function parseReps(text: string): number | undefined {
    return /^\d+$/.test(text.trim()) ? Number(text.trim()) : undefined;
}

/** As séries do último treino com o exercício feito: o do item ou a alternativa escolhida. */
export function lastSets(item: SessionItem, exerciseId: string | undefined): RecordedSet[] {
    if (!exerciseId || exerciseId === item.exercise?.id) return item.last ?? [];
    return item.alternativesLast?.find((each) => each.exerciseId === exerciseId)?.sets ?? [];
}

/**
 * O que vem preenchido na série: o último treino do exercício (a mesma série, ou a última feita); sem ele, o
 * prescrito, com o mínimo da faixa (8–12 vem 8) e a carga sugerida (sem carga, 0).
 */
export function prefill(item: SessionItem, setNumber: number, exerciseId: string | undefined): SetDraft {
    const last = lastSets(item, exerciseId);
    const previous = last.find((each) => each.setNumber === setNumber) ?? last[last.length - 1];
    if (previous) return { load: loadText(previous.loadKg), reps: String(previous.reps ?? 0) };
    return { load: loadText(item.loadKg), reps: String(item.repsMin ?? 0) };
}

export const FEELING_LABELS: Record<string, string> = {
    '1': 'Muito fácil',
    '2': 'Fácil',
    '3': 'Moderado',
    '4': 'Difícil',
    '5': 'Exaustivo',
};
