import { useQuery } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router';

import { messageOf } from './messages';
import { CLIENTS_KEY, useClientApi } from './useClientApi';

/** Clientes com vínculo ativo do profissional (DEV-0043). A lista vem da API, que só devolve os vinculados. */
export function ClientsPage() {
    const api = useClientApi();
    const location = useLocation();
    const notice = (location.state as { notice?: string } | null)?.notice;
    const clients = useQuery({ queryKey: CLIENTS_KEY, queryFn: () => api.listClients() });

    return (
        <section className="space-y-3">
            <h3 className="text-base font-semibold">Clientes</h3>
            {notice ? (
                <p role="status" className="text-sm text-muted-foreground">
                    {notice}
                </p>
            ) : null}
            {clients.isPending ? <p role="status">Carregando…</p> : null}
            {clients.isError ? <p role="alert">{messageOf(clients.error)}</p> : null}
            {clients.data && clients.data.length === 0 ? (
                <p className="text-muted-foreground">
                    Nenhum cliente vinculado ainda. Convide um cliente na aba Convites.
                </p>
            ) : null}
            <ul className="space-y-2">
                {clients.data?.map((client) => (
                    <li key={client.linkId}>
                        <Link className="underline" to={`/app/clientes/${client.clientId}`}>
                            {client.clientName}
                        </Link>
                        <span className="ml-2 text-sm text-muted-foreground">
                            desde{' '}
                            {client.startedAt ? new Date(client.startedAt).toLocaleDateString('pt-BR') : ''}
                        </span>
                    </li>
                ))}
            </ul>
        </section>
    );
}
