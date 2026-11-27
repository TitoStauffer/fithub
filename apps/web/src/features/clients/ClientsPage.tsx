import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';

import { useAuth } from '@/features/auth/authContext';
import { useTrainingApi } from '@/features/exercises/useTrainingApi';
import { useNutritionApi } from '@/features/foods/useNutritionApi';

import {
    activityOf,
    inactiveThreshold,
    latestActivity,
    MAX_INACTIVE_DAYS,
    parseThreshold,
    type Activity,
} from './inactivity';
import { messageOf } from './messages';
import { CLIENTS_KEY, useClientApi } from './useClientApi';

function activityText(activity: Activity): string {
    if (activity.kind === 'none') return 'Sem atividade registrada';
    if (activity.kind === 'inactive') return `Inativo há ${activity.days} dias`;
    return `Última atividade em ${new Date(activity.lastActivityAt).toLocaleDateString('pt-BR')}`;
}

/**
 * Clientes com vínculo ativo do profissional (DEV-0043). A lista vem da API, que só devolve os vinculados. Cada
 * cliente traz a última atividade (DEV-0097, FR-59): treino finalizado para o personal, refeição registrada para a
 * nutricionista. Quem passa do limite de dias (na URL, `?inativos=`) aparece sinalizado.
 */
export function ClientsPage({ now = new Date() }: { now?: Date }) {
    const api = useClientApi();
    const training = useTrainingApi();
    const nutrition = useNutritionApi();
    const { user } = useAuth();
    const personal = user?.roles.includes('personal') ?? false;
    const nutritionist = user?.roles.includes('nutritionist') ?? false;
    const location = useLocation();
    const notice = (location.state as { notice?: string } | null)?.notice;
    const [params, setParams] = useSearchParams();
    const threshold = inactiveThreshold(params.get('inativos'));
    // O campo guarda o que está sendo digitado; a URL só recebe um limite válido (apagar para digitar não volta a 7).
    const [draft, setDraft] = useState(String(threshold));
    // A URL pode mudar na mesma rota (voltar no navegador, link para `?inativos=14`): o campo acompanha o limite novo.
    // Ajuste durante a renderização, sem efeito, para o campo nunca mostrar um limite que a lista não usa (DEV-0159).
    const [shownThreshold, setShownThreshold] = useState(threshold);
    if (threshold !== shownThreshold) {
        setShownThreshold(threshold);
        setDraft(String(threshold));
    }
    const [onlyInactive, setOnlyInactive] = useState(false);

    const clients = useQuery({ queryKey: CLIENTS_KEY, queryFn: () => api.listClients() });
    const trainingActivity = useQuery({
        queryKey: ['personal', 'clients', 'activity'],
        queryFn: () => training.clientsActivity(),
        enabled: personal,
        retry: false,
    });
    const dietActivity = useQuery({
        queryKey: ['nutritionist', 'clients', 'activity'],
        queryFn: () => nutrition.patientsActivity(),
        enabled: nutritionist,
        retry: false,
    });
    // A sinalização só aparece com a atividade de todos os papéis lida: sem ela, ninguém é marcado à toa.
    const activityReady =
        (!personal || trainingActivity.isSuccess) && (!nutritionist || dietActivity.isSuccess);
    const activityError = trainingActivity.error ?? dietActivity.error;
    const latest = latestActivity([trainingActivity.data ?? [], dietActivity.data ?? []]);
    const rows = (clients.data ?? []).map((client) => ({
        client,
        activity: activityReady ? activityOf(latest.get(client.clientId ?? ''), now, threshold) : undefined,
    }));
    const visible = onlyInactive
        ? rows.filter((row) => row.activity && row.activity.kind !== 'active')
        : rows;

    return (
        <section className="space-y-3">
            <h3 className="text-base font-semibold">Clientes</h3>
            {notice ? (
                <p role="status" className="text-sm text-muted-foreground">
                    {notice}
                </p>
            ) : null}
            <div className="flex flex-wrap items-center gap-4 text-sm">
                <label className="flex items-center gap-2">
                    Inativo depois de (dias)
                    <input
                        type="number"
                        min={1}
                        max={MAX_INACTIVE_DAYS}
                        className="w-20 rounded border px-2 py-1"
                        value={draft}
                        aria-invalid={parseThreshold(draft) === undefined}
                        onChange={(event) => {
                            setDraft(event.target.value);
                            const valid = parseThreshold(event.target.value);
                            if (valid === undefined) return;
                            const next = new URLSearchParams(params);
                            next.set('inativos', String(valid));
                            setParams(next, { replace: true });
                        }}
                    />
                </label>
                <label className="flex items-center gap-2">
                    <input
                        type="checkbox"
                        checked={onlyInactive}
                        onChange={(event) => setOnlyInactive(event.target.checked)}
                    />
                    Só os inativos
                </label>
            </div>
            {clients.isPending ? <p role="status">Carregando…</p> : null}
            {clients.isError ? <p role="alert">{messageOf(clients.error)}</p> : null}
            {activityError ? (
                <p role="alert">Não foi possível ler a última atividade: {messageOf(activityError)}</p>
            ) : null}
            {clients.data && clients.data.length === 0 ? (
                <p className="text-muted-foreground">
                    Nenhum cliente vinculado ainda. Convide um cliente na aba Convites.
                </p>
            ) : null}
            {/* Só com a atividade lida: carregando ou com erro, a tela ainda não sabe se há inativos (DEV-0159). */}
            {activityReady && clients.data && clients.data.length > 0 && visible.length === 0 ? (
                <p className="text-muted-foreground">Nenhum cliente inativo há mais de {threshold} dias.</p>
            ) : null}
            <ul className="space-y-2">
                {visible.map(({ client, activity }) => (
                    <li key={client.linkId}>
                        <Link className="underline" to={`/app/clientes/${client.clientId}`}>
                            {client.clientName}
                        </Link>
                        <span className="ml-2 text-sm text-muted-foreground">
                            desde{' '}
                            {client.startedAt ? new Date(client.startedAt).toLocaleDateString('pt-BR') : ''}
                        </span>
                        {activity ? (
                            <span
                                className={
                                    activity.kind === 'active'
                                        ? 'ml-2 text-sm text-muted-foreground'
                                        : 'ml-2 text-sm font-medium text-destructive'
                                }
                            >
                                · {activityText(activity)}
                            </span>
                        ) : null}
                    </li>
                ))}
            </ul>
        </section>
    );
}
