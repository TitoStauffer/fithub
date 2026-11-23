import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { messageOf } from '@/features/clients/messages';
import { useClientApi } from '@/features/clients/useClientApi';

import { ORIGINS } from './assessmentForm';

const TYPES: Record<string, string> = { PRESENTIAL: 'Presencial', ONLINE: 'Online' };

/**
 * Histórico de avaliações do cliente vinculado (DEV-0049, FR-19): cada % de gordura aparece com a origem, porque os
 * métodos não são comparáveis entre si.
 */
export function AssessmentHistory({ clientId }: { clientId: string }) {
    const api = useClientApi();
    const history = useQuery({
        queryKey: ['professional', 'client', clientId, 'assessments'],
        queryFn: () => api.clientAssessments(clientId),
        retry: false,
    });

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-3">
                <h4 className="font-medium">Avaliações</h4>
                <Link className="underline" to={`/app/clientes/${clientId}/avaliacoes/nova`}>
                    Nova avaliação presencial
                </Link>
            </div>
            {history.isPending ? <p role="status">Carregando as avaliações…</p> : null}
            {history.isError ? <p role="alert">{messageOf(history.error)}</p> : null}
            {history.data && history.data.length === 0 ? (
                <p className="text-muted-foreground">Nenhuma avaliação ainda.</p>
            ) : null}
            <ul className="space-y-2">
                {history.data?.map((assessment) => (
                    <li key={assessment.id} className="rounded border p-2">
                        <p>
                            {assessment.measuredOn
                                ? new Date(`${assessment.measuredOn}T12:00:00`).toLocaleDateString('pt-BR')
                                : ''}{' '}
                            · {TYPES[assessment.type ?? ''] ?? ''} · {assessment.weightKg} kg
                        </p>
                        {(assessment.bodyFat ?? []).map((estimate) => (
                            <p key={estimate.origin} className="text-sm">
                                % de gordura ({ORIGINS[estimate.origin ?? ''] ?? estimate.origin}):{' '}
                                {estimate.percent}%
                            </p>
                        ))}
                    </li>
                ))}
            </ul>
        </div>
    );
}
