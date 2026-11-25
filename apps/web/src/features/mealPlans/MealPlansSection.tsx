import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { useNutritionApi } from '@/features/foods/useNutritionApi';

import { STATUS_LABELS, mealPlansKey } from './mealPlanForm';

/** Planos alimentares que a nutricionista criou para o paciente vinculado (DEV-0085), com o link para o editor. */
export function MealPlansSection({ clientId }: { clientId: string }) {
    const api = useNutritionApi();
    const plans = useQuery({
        queryKey: mealPlansKey(clientId),
        queryFn: () => api.listMealPlans(clientId),
        retry: false,
    });

    return (
        <section aria-label="Planos alimentares" className="space-y-2">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Planos alimentares</h4>
                <Button asChild size="sm">
                    <Link to={`/app/clientes/${clientId}/planos/novo`}>Novo plano</Link>
                </Button>
            </div>
            {plans.isPending ? <p role="status">Carregando os planos…</p> : null}
            {plans.isError ? <p role="alert">{messageOf(plans.error)}</p> : null}
            {plans.data && plans.data.length === 0 ? (
                <p className="text-muted-foreground">Nenhum plano ainda.</p>
            ) : null}
            {plans.data && plans.data.length > 0 ? (
                <ul className="space-y-1">
                    {plans.data.map((plan) => (
                        <li key={plan.id}>
                            <Link
                                className="underline"
                                to={`/app/clientes/${clientId}/planos/${plan.id ?? ''}`}
                            >
                                {plan.name}
                            </Link>{' '}
                            <span className="text-sm text-muted-foreground">
                                · {STATUS_LABELS[plan.status ?? ''] ?? plan.status} · versão {plan.version} ·
                                início em {plan.startsOn}
                            </span>
                        </li>
                    ))}
                </ul>
            ) : null}
        </section>
    );
}
