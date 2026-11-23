import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { AccessEndedError } from '@/lib/clientApi';

import { CONDITIONS, GENDERS, GOALS, messageOf } from './messages';
import { CLIENTS_KEY, useClientApi } from './useClientApi';

/**
 * Cliente vinculado visto pelo profissional (DEV-0043): perfil e saúde vindos da API, que decide o acesso. Com o
 * vínculo encerrado, a próxima ação mostra que o acesso acabou (PRD §14). Encerrar pede confirmação.
 */
export function ClientDetailPage() {
    const { clientId = '' } = useParams();
    const api = useClientApi();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [confirming, setConfirming] = useState(false);

    const profile = useQuery({
        queryKey: ['professional', 'client', clientId, 'profile'],
        queryFn: () => api.clientProfile(clientId),
        retry: false,
    });
    const health = useQuery({
        queryKey: ['professional', 'client', clientId, 'health'],
        queryFn: () => api.clientHealth(clientId),
        retry: false,
        enabled: profile.isSuccess,
    });
    const clients = useQuery({ queryKey: CLIENTS_KEY, queryFn: () => api.listClients() });
    const linkId = clients.data?.find((client) => client.clientId === clientId)?.linkId;

    const end = useMutation({
        mutationFn: () => api.endLink(linkId ?? ''),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: CLIENTS_KEY });
            void navigate('/app', { state: { notice: 'Vínculo encerrado.' } });
        },
    });

    const accessEnded = profile.error instanceof AccessEndedError || health.error instanceof AccessEndedError;
    if (accessEnded) {
        return (
            <section className="space-y-3">
                <p role="alert">O acesso a este cliente acabou: o vínculo foi encerrado.</p>
                <Link className="underline" to="/app">
                    Voltar para os clientes
                </Link>
            </section>
        );
    }

    return (
        <section className="space-y-4">
            <Link className="underline" to="/app">
                Voltar para os clientes
            </Link>
            {profile.isPending ? <p role="status">Carregando…</p> : null}
            {profile.isError ? <p role="alert">{messageOf(profile.error)}</p> : null}
            {profile.data ? (
                <div className="space-y-1">
                    <h3 className="text-base font-semibold">{profile.data.name}</h3>
                    <p>{profile.data.age} anos</p>
                    <p>Gênero: {GENDERS[profile.data.gender ?? ''] ?? '—'}</p>
                    <p>Altura: {profile.data.heightCm} cm</p>
                    <p>Objetivo: {GOALS[profile.data.goal ?? ''] ?? '—'}</p>
                </div>
            ) : null}
            {profile.data ? (
                <div className="space-y-1">
                    <h4 className="font-medium">Saúde</h4>
                    {health.isPending ? <p role="status">Carregando a saúde…</p> : null}
                    {health.isError ? <p role="alert">{messageOf(health.error)}</p> : null}
                    {health.isSuccess && health.data === null ? (
                        <p className="text-muted-foreground">
                            O cliente ainda não informou os dados de saúde.
                        </p>
                    ) : null}
                    {health.data ? (
                        <>
                            <p>
                                Condições:{' '}
                                {(health.data.conditions ?? []).length === 0
                                    ? 'nenhuma'
                                    : (health.data.conditions ?? [])
                                          .map((item) => CONDITIONS[item] ?? item)
                                          .join(', ')}
                            </p>
                            {health.data.notes ? <p>Observações: {health.data.notes}</p> : null}
                        </>
                    ) : null}
                </div>
            ) : null}
            {profile.data && linkId ? (
                <div className="space-y-2">
                    {!confirming ? (
                        <Button variant="outline" onClick={() => setConfirming(true)}>
                            Encerrar vínculo
                        </Button>
                    ) : (
                        <div role="group" aria-label="Confirmar encerramento" className="space-y-2">
                            <p>Você perde o acesso aos dados deste cliente na hora. Encerrar mesmo?</p>
                            <Button disabled={end.isPending} onClick={() => end.mutate()}>
                                Confirmar encerramento
                            </Button>
                            <Button variant="outline" onClick={() => setConfirming(false)}>
                                Cancelar
                            </Button>
                        </div>
                    )}
                    {end.isError ? <p role="alert">{messageOf(end.error)}</p> : null}
                </div>
            ) : null}
        </section>
    );
}
