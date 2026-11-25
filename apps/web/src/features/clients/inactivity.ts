/** Os dias sem atividade que fazem o cliente aparecer como inativo (DEV-0097): de 1 a 60, padrão 7. */
export const DEFAULT_INACTIVE_DAYS = 7;
export const MAX_INACTIVE_DAYS = 60;

/** O limite digitado, se for um inteiro de 1 a 60; senão, `undefined`. */
export function parseThreshold(text: string | null): number | undefined {
    if (text === null || text.trim() === '') return undefined;
    const value = Number(text);
    return Number.isInteger(value) && value >= 1 && value <= MAX_INACTIVE_DAYS ? value : undefined;
}

/** O limite lido da URL (`?inativos=`); fora de 1 a 60 ou não numérico, o padrão. */
export function inactiveThreshold(param: string | null): number {
    return parseThreshold(param) ?? DEFAULT_INACTIVE_DAYS;
}

/** Dias inteiros entre o dia da última atividade e hoje, no fuso do navegador. */
export function daysSince(lastActivityAt: string, now: Date): number {
    const last = new Date(lastActivityAt);
    const lastDay = Date.UTC(last.getFullYear(), last.getMonth(), last.getDate());
    const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
    return Math.round((today - lastDay) / 86_400_000);
}

/** A situação do cliente: sem atividade registrada, inativo há N dias (mais que o limite) ou ativo. */
export type Activity =
    { kind: 'none' } | { kind: 'inactive'; days: number } | { kind: 'active'; lastActivityAt: string };

export function activityOf(lastActivityAt: string | undefined, now: Date, threshold: number): Activity {
    // Sem nenhum registro, conta como inativo (decisão do refinamento).
    if (!lastActivityAt) return { kind: 'none' };
    const days = daysSince(lastActivityAt, now);
    return days > threshold ? { kind: 'inactive', days } : { kind: 'active', lastActivityAt };
}

/** A última atividade de cada cliente, juntando os serviços lidos (personal e nutricionista na mesma conta). */
export function latestActivity(
    lists: { clientId?: string; lastActivityAt?: string }[][],
): Map<string, string> {
    const latest = new Map<string, string>();
    for (const list of lists) {
        for (const item of list) {
            if (!item.clientId || !item.lastActivityAt) continue;
            const current = latest.get(item.clientId);
            if (!current || new Date(item.lastActivityAt) > new Date(current)) {
                latest.set(item.clientId, item.lastActivityAt);
            }
        }
    }
    return latest;
}
