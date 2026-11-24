import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';

import { STATUS_LABELS, plansKey } from './planForm';

/** Fichas que o personal criou para o aluno vinculado (DEV-0063), com o link para o editor. */
export function PlansSection({ clientId }: { clientId: string }) {
    const api = useTrainingApi();
    const plans = useQuery({
        queryKey: plansKey(clientId),
        queryFn: () => api.listPlans(clientId),
        retry: false,
    });

    return (
        <section aria-label="Fichas de treino" className="space-y-2">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Fichas de treino</h4>
                <Button asChild size="sm">
                    <Link to={`/app/clientes/${clientId}/fichas/nova`}>Nova ficha</Link>
                </Button>
            </div>
            {plans.isPending ? <p role="status">Carregando as fichas…</p> : null}
            {plans.isError ? <p role="alert">{messageOf(plans.error)}</p> : null}
            {plans.data && plans.data.length === 0 ? (
                <p className="text-muted-foreground">Nenhuma ficha ainda.</p>
            ) : null}
            {plans.data && plans.data.length > 0 ? (
                <ul className="space-y-1">
                    {plans.data.map((plan) => (
                        <li key={plan.id}>
                            <Link
                                className="underline"
                                to={`/app/clientes/${clientId}/fichas/${plan.id ?? ''}`}
                            >
                                {plan.name}
                            </Link>{' '}
                            <span className="text-sm text-muted-foreground">
                                · {STATUS_LABELS[plan.status ?? ''] ?? plan.status} · versão {plan.version} ·
                                vence em {plan.expiresOn}
                            </span>
                        </li>
                    ))}
                </ul>
            ) : null}
        </section>
    );
}
