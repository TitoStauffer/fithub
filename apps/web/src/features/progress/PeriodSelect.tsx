import { PERIODS, type PeriodWeeks } from './progressPeriod';

/** A escolha do período da evolução: 4, 8 ou 12 semanas até hoje (DEV-0094). */
export function PeriodSelect({
    weeks,
    onChange,
}: {
    weeks: PeriodWeeks;
    onChange: (weeks: PeriodWeeks) => void;
}) {
    return (
        <label className="flex items-center gap-2 text-sm">
            Período
            <select
                className="rounded border px-2 py-1"
                value={weeks}
                onChange={(event) => onChange(Number(event.target.value) as PeriodWeeks)}
            >
                {PERIODS.map((option) => (
                    <option key={option} value={option}>
                        {option} semanas
                    </option>
                ))}
            </select>
        </label>
    );
}
