import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import type { Assessment } from '@/lib/clientApi';
import { messageOf } from '@/features/clients/messages';
import { useClientApi } from '@/features/clients/useClientApi';

const POSES = [
    ['FRONT', 'Frente'],
    ['SIDE', 'Lado'],
    ['BACK', 'Costas'],
] as const;

type Pose = (typeof POSES)[number][0];

function dateOf(assessment: Assessment | undefined): string {
    return assessment?.measuredOn
        ? new Date(`${assessment.measuredOn}T12:00:00`).toLocaleDateString('pt-BR')
        : '';
}

/**
 * Comparativo de fotos lado a lado entre duas avaliações do cliente vinculado (DEV-0051, FR-20). As URLs
 * pré-assinadas são pedidas na hora de mostrar e pedidas de novo quando vencem; sem vínculo, a API responde 404.
 */
export function PhotoComparison({ clientId }: { clientId: string }) {
    const api = useClientApi();
    const history = useQuery({
        queryKey: ['professional', 'client', clientId, 'assessments'],
        queryFn: () => api.clientAssessments(clientId),
        retry: false,
    });
    const [first, setFirst] = useState('');
    const [second, setSecond] = useState('');
    const [pose, setPose] = useState<Pose>('FRONT');

    const assessments = history.data ?? [];
    if (assessments.length < 2) return null;

    const select = (label: string, value: string, onChange: (value: string) => void) => (
        <label className="block space-y-1">
            <span className="text-sm font-medium">{label}</span>
            <select
                className="block w-full rounded border px-2 py-1"
                value={value}
                onChange={(event) => onChange(event.target.value)}
            >
                <option value="">Escolha a data</option>
                {assessments.map((assessment) => (
                    <option key={assessment.id} value={assessment.id}>
                        {dateOf(assessment)}
                    </option>
                ))}
            </select>
        </label>
    );

    return (
        <div className="space-y-2">
            <h4 className="font-medium">Comparar fotos</h4>
            <div className="grid gap-3 sm:grid-cols-3">
                {select('Primeira data', first, setFirst)}
                {select('Segunda data', second, setSecond)}
                <label className="block space-y-1">
                    <span className="text-sm font-medium">Pose</span>
                    <select
                        className="block w-full rounded border px-2 py-1"
                        value={pose}
                        onChange={(event) => setPose(event.target.value as Pose)}
                    >
                        {POSES.map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </label>
            </div>
            <div className="grid grid-cols-2 gap-3">
                {[first, second].map((assessmentId, index) =>
                    assessmentId ? (
                        <Side
                            key={`${index}-${assessmentId}-${pose}`}
                            clientId={clientId}
                            assessment={assessments.find((item) => item.id === assessmentId)}
                            pose={pose}
                        />
                    ) : (
                        <p key={index} className="text-muted-foreground">
                            Escolha a {index === 0 ? 'primeira' : 'segunda'} data.
                        </p>
                    ),
                )}
            </div>
        </div>
    );
}

function Side({ clientId, assessment, pose }: { clientId: string; assessment?: Assessment; pose: Pose }) {
    const api = useClientApi();
    const assessmentId = assessment?.id ?? '';
    const photos = useQuery({
        queryKey: ['professional', 'client', clientId, 'assessments', assessmentId, 'photos'],
        queryFn: () => api.clientAssessmentPhotos(clientId, assessmentId),
        // A URL vale 5 minutos: cada vez que a foto aparece, ela é pedida de novo, nunca reaproveitada do cache.
        staleTime: 0,
        gcTime: 0,
        refetchOnMount: 'always',
        retry: false,
    });
    const photo = photos.data?.find((item) => item.pose === pose);
    // Foto que não carregou (URL vencida): pede uma URL nova uma vez; se a nova também falhar, avisa em vez de
    // insistir.
    const [retried, setRetried] = useState(false);
    const [broken, setBroken] = useState(false);
    const label = POSES.find(([value]) => value === pose)?.[1].toLowerCase() ?? '';

    return (
        <figure className="space-y-1">
            <figcaption className="font-medium">{dateOf(assessment)}</figcaption>
            {photos.isPending ? <p role="status">Carregando a foto…</p> : null}
            {photos.isError ? <p role="alert">{messageOf(photos.error)}</p> : null}
            {photos.data && !photo ? (
                <p className="text-muted-foreground">Sem foto de {label} nesta avaliação.</p>
            ) : null}
            {photo?.url && broken ? (
                <p role="alert">Não foi possível carregar a foto. Tente de novo mais tarde.</p>
            ) : null}
            {photo?.url && !broken ? (
                <img
                    src={photo.url}
                    alt={`Foto de ${label} em ${dateOf(assessment)}`}
                    className="w-full rounded border object-contain"
                    // URL vencida (a página ficou aberta mais de 5 minutos): pede uma nova.
                    onError={() => {
                        if (retried) {
                            setBroken(true);
                            return;
                        }
                        setRetried(true);
                        void photos.refetch();
                    }}
                />
            ) : null}
        </figure>
    );
}
