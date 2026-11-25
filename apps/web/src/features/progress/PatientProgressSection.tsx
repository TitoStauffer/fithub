import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { messageOf } from '@/features/clients/messages';
import { useNutritionApi } from '@/features/foods/useNutritionApi';

import { BodyEvolution } from './BodyEvolution';
import { LineChart } from './LineChart';
import { PeriodSelect } from './PeriodSelect';
import { formatDay, periodStart, type PeriodWeeks } from './progressPeriod';

const NUMBER = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/**
 * Evolução do paciente vinculado para a nutricionista (DEV-0095, FR-57): aderência do período com a série diária, as
 * alternativas mais usadas, os desvios com o texto e a foto (pela URL temporária) e a evolução corporal. Cada bloco
 * diz o que falta quando não há dado.
 */
export function PatientProgressSection({ clientId, now = new Date() }: { clientId: string; now?: Date }) {
    const nutrition = useNutritionApi();
    const [weeks, setWeeks] = useState<PeriodWeeks>(4);
    const from = periodStart(now, weeks);

    const progress = useQuery({
        queryKey: ['nutritionist', 'client', clientId, 'diet-progress', from],
        queryFn: () => nutrition.clientDietProgress(clientId, from),
        retry: false,
    });
    const adherence = progress.data?.adherence;
    const alternatives = progress.data?.alternatives ?? [];
    const deviations = progress.data?.deviations ?? [];

    return (
        <section aria-label="Evolução da dieta" className="space-y-3">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Evolução</h4>
                <PeriodSelect weeks={weeks} onChange={setWeeks} />
            </div>

            <div className="space-y-1">
                <h5 className="text-sm font-medium">Aderência</h5>
                {progress.isPending ? <p role="status">Carregando a aderência…</p> : null}
                {progress.isError ? <p role="alert">{messageOf(progress.error)}</p> : null}
                {adherence && adherence.percent === undefined ? (
                    <p className="text-muted-foreground">Nenhum plano publicado no período.</p>
                ) : null}
                {adherence && adherence.percent !== undefined ? (
                    <>
                        <p>
                            {NUMBER.format(adherence.done ?? 0)} de {adherence.prescribed} refeições
                            prescritas ({NUMBER.format(adherence.percent)}%)
                        </p>
                        <LineChart
                            title="Refeições feitas por dia"
                            unit="refeições"
                            points={(adherence.days ?? []).map((day) => ({
                                label: formatDay(day.day),
                                value: day.done ?? 0,
                            }))}
                        />
                    </>
                ) : null}
            </div>

            {progress.data ? (
                <div className="space-y-1">
                    <h5 className="text-sm font-medium">Alternativas mais usadas</h5>
                    {alternatives.length === 0 ? (
                        <p className="text-muted-foreground">Nenhuma alternativa usada no período.</p>
                    ) : (
                        <ol className="list-decimal pl-5 text-sm">
                            {alternatives.map((alternative) => (
                                <li key={alternative.foodId}>
                                    {alternative.foodName}: {alternative.times}{' '}
                                    {alternative.times === 1 ? 'vez' : 'vezes'}
                                </li>
                            ))}
                        </ol>
                    )}
                </div>
            ) : null}

            {progress.data ? (
                <div className="space-y-1">
                    <h5 className="text-sm font-medium">Desvios</h5>
                    {deviations.length === 0 ? (
                        <p className="text-muted-foreground">Nenhum desvio registrado no período.</p>
                    ) : (
                        <ul aria-label="Desvios" className="space-y-2">
                            {deviations.map((deviation) => (
                                <li key={deviation.logId} className="rounded border p-2 text-sm">
                                    <p className="font-medium">
                                        {formatDay(deviation.day)} · {deviation.mealName}
                                    </p>
                                    {deviation.deviation ? <p>{deviation.deviation}</p> : null}
                                    {deviation.photoUrl ? (
                                        <a
                                            className="underline"
                                            href={deviation.photoUrl}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                        >
                                            Ver foto do prato
                                        </a>
                                    ) : null}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            ) : null}

            <BodyEvolution clientId={clientId} />
        </section>
    );
}
