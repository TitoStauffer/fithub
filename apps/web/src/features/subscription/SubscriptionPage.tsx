import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { useApiFetch } from '@/features/auth/useApiFetch';
import { fetchMySubscription, startSubscriptionCheckout, type Subscription } from '@/lib/api';
import { config } from '@/lib/config';

import { DATE, daysLeft, offerText, subscriptionKey } from './subscription';

/**
 * Plano e assinatura do profissional (DEV-0102, PRD seção 9.2): o estado, o preço da oferta e o checkout do Asaas.
 * Na volta do checkout, o aviso só diz o que aconteceu: quem confirma o pagamento é o webhook do Asaas, não a volta.
 */
export function SubscriptionPage({ now = new Date() }: { now?: Date }) {
    const apiFetch = useApiFetch();
    const queryClient = useQueryClient();
    const [search] = useSearchParams();
    const back = search.get('retorno');
    const notice = useRef<HTMLDivElement>(null);

    const subscription = useQuery({
        queryKey: subscriptionKey,
        queryFn: () => fetchMySubscription(apiFetch, config.accountApiUrl),
    });

    // Na volta do checkout, o foco vai para o aviso, e o estado é lido de novo.
    useEffect(() => {
        if (back === 'sucesso' || back === 'cancelado') {
            notice.current?.focus();
            void queryClient.invalidateQueries({ queryKey: subscriptionKey });
        }
    }, [back, queryClient]);

    const checkout = useMutation({
        mutationFn: () => startSubscriptionCheckout(apiFetch, config.accountApiUrl),
        onSuccess: (created) => window.location.assign(created.checkoutUrl),
    });

    return (
        <section className="space-y-4" aria-labelledby="assinatura-titulo">
            <h3 id="assinatura-titulo" className="text-base font-semibold">
                Plano e assinatura
            </h3>

            {back === 'sucesso' ? (
                <div ref={notice} tabIndex={-1} role="status" className="rounded-md border p-3">
                    Pagamento enviado. A confirmação chega do Asaas em instantes; atualize esta página para
                    ver o estado novo.
                </div>
            ) : null}
            {back === 'cancelado' ? (
                <div ref={notice} tabIndex={-1} role="status" className="rounded-md border p-3">
                    Pagamento cancelado. Nada foi cobrado.
                </div>
            ) : null}

            {subscription.isPending ? <p>Carregando a assinatura…</p> : null}
            {subscription.isError ? (
                <div role="alert" className="space-y-2">
                    <p>Não foi possível carregar a assinatura. {subscription.error.message}</p>
                    <Button variant="outline" size="sm" onClick={() => void subscription.refetch()}>
                        Tentar de novo
                    </Button>
                </div>
            ) : null}

            {subscription.data ? (
                <>
                    <SubscriptionStatus subscription={subscription.data} now={now} />
                    {subscription.data.offer ? (
                        <p>
                            <span className="font-medium">Plano mensal:</span>{' '}
                            {offerText(subscription.data.offer)}
                        </p>
                    ) : null}
                    {subscription.data.status === 'ACTIVE' ? null : (
                        <div className="space-y-2">
                            <Button onClick={() => checkout.mutate()} disabled={checkout.isPending}>
                                {checkout.isPending ? 'Abrindo o pagamento…' : 'Assinar com cartão'}
                            </Button>
                            {checkout.isError ? <p role="alert">{checkout.error.message}</p> : null}
                        </div>
                    )}
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => void subscription.refetch()}
                        disabled={subscription.isFetching}
                    >
                        Atualizar o estado
                    </Button>
                </>
            ) : null}
        </section>
    );
}

/** O estado em texto, nunca só por cor (critério de acessibilidade). */
function SubscriptionStatus({ subscription, now }: { subscription: Subscription; now: Date }) {
    if (subscription.status === 'TRIAL') {
        const days = daysLeft(subscription.trialEndsAt, now);
        return (
            <p>
                <span className="font-medium">Estado:</span> em teste grátis.{' '}
                {days === 1 ? 'Falta 1 dia' : `Faltam ${days} dias`} (até{' '}
                {DATE.format(new Date(subscription.trialEndsAt))}).
            </p>
        );
    }
    if (subscription.status === 'ACTIVE') {
        return (
            <p>
                <span className="font-medium">Estado:</span> ativa, até{' '}
                {DATE.format(new Date(subscription.validUntil))}.
            </p>
        );
    }
    return (
        <p role="alert">
            <span className="font-medium">Estado:</span> vencida. Criar, editar e publicar fichas e planos
            fica bloqueado até você assinar; o que já foi entregue continua com os clientes.
        </p>
    );
}
