import { ApiProblemError, type SubscriptionOffer } from '@/lib/api';

export const subscriptionKey = ['professional', 'subscription'] as const;

const DAY_MS = 24 * 60 * 60 * 1000;
const MONEY = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
/** Data curta no fuso de São Paulo, como no resto da web. */
export const DATE = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'America/Sao_Paulo' });

/** Dias que faltam até o instante, contando o dia de hoje pela metade como um dia inteiro. */
export function daysLeft(until: string, now: Date): number {
    return Math.max(0, Math.ceil((new Date(until).getTime() - now.getTime()) / DAY_MS));
}

/** O texto do preço da oferta atual (PRD seção 9.2): o de lançamento nos primeiros meses, quando vale. */
export function offerText(offer: SubscriptionOffer): string {
    const regular = MONEY.format(offer.regularValue);
    if (!offer.launchPrice) return `${regular} por mês.`;
    const months = offer.launchMonths === 1 ? '1º mês' : `${offer.launchMonths} primeiros meses`;
    return `${MONEY.format(offer.value)} por mês nos ${months}, depois ${regular} por mês.`;
}

/**
 * O 403 de criar, editar ou publicar ficha e plano alimentar: nos editores, a rota já exige o papel, então a recusa é
 * da assinatura vencida (BR-14, Code Review do PR #286).
 */
export function isSubscriptionExpired(error: unknown): boolean {
    return error instanceof ApiProblemError && error.status === 403;
}
