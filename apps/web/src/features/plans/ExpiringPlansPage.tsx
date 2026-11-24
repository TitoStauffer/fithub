import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';

function todayIso(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Fichas vencendo (DEV-0064, FR-32): as fichas publicadas dos alunos vinculados que vencem nos próximos 7 dias ou já
 * venceram, cada uma levando ao aluno.
 */
export function ExpiringPlansPage({ now = new Date() }: { now?: Date }) {
    const api = useTrainingApi();
    const plans = useQuery({
        queryKey: ['personal', 'plans', 'expiring'],
        queryFn: () => api.expiringPlans(),
    });
    const today = todayIso(now);

    return (
        <section className="space-y-3">
            <h3 className="text-base font-semibold">Fichas vencendo</h3>
            <p className="text-sm text-muted-foreground">
                Fichas publicadas que vencem nos próximos 7 dias ou já venceram.
            </p>
            {plans.isPending ? <p role="status">Carregando…</p> : null}
            {plans.isError ? (
                <div role="alert" className="space-y-2">
                    <p>{messageOf(plans.error)}</p>
                    <Button type="button" size="sm" variant="outline" onClick={() => void plans.refetch()}>
                        Tentar de novo
                    </Button>
                </div>
            ) : null}
            {plans.data && plans.data.length === 0 ? <p>Nenhuma ficha vencendo agora.</p> : null}
            {plans.data && plans.data.length > 0 ? (
                <ul className="space-y-2">
                    {plans.data.map((plan) => {
                        const expired = (plan.expiresOn ?? '') < today;
                        return (
                            <li key={plan.planId} className="space-x-2">
                                <Link className="underline" to={`/app/clientes/${plan.clientId ?? ''}`}>
                                    {plan.clientName ?? 'Aluno'}
                                </Link>
                                <span>· {plan.name}</span>
                                <span>
                                    · {expired ? 'venceu em' : 'vence em'} {plan.expiresOn}
                                </span>
                                {expired ? <strong className="text-red-700">Vencida</strong> : null}
                            </li>
                        );
                    })}
                </ul>
            ) : null}
        </section>
    );
}
