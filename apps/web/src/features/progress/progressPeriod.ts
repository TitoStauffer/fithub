/** Os períodos da evolução, em semanas (DEV-0094). */
export const PERIODS = [4, 8, 12] as const;
export type PeriodWeeks = (typeof PERIODS)[number];

/** O primeiro dia do período que termina hoje, em yyyy-mm-dd no fuso do aparelho. */
export function periodStart(now: Date, weeks: PeriodWeeks): string {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (weeks * 7 - 1));
    const month = String(start.getMonth() + 1).padStart(2, '0');
    const day = String(start.getDate()).padStart(2, '0');
    return `${start.getFullYear()}-${month}-${day}`;
}

/** A data yyyy-mm-dd no formato brasileiro, sem passar pelo fuso. */
export function formatDay(date: string | undefined): string {
    if (!date) return '';
    const [year, month, day] = date.split('-');
    return `${day}/${month}/${year}`;
}
