import { useQuery } from '@tanstack/react-query';

import { Button } from '@/components/ui/button';

import { fetchAccountHealth } from './accountHealth';

type ServiceStatusProps = {
    /** Injetável nos testes; o padrão consulta o account-service. */
    checkHealth?: () => Promise<boolean>;
};

/**
 * Mostra se a API do profissional responde. Serve de exemplo do padrão de dados do app: TanStack Query
 * com os estados de carregando, sucesso e erro, e uma ação para tentar de novo.
 */
export function ServiceStatus({ checkHealth = fetchAccountHealth }: ServiceStatusProps) {
    const health = useQuery({ queryKey: ['account-health'], queryFn: () => checkHealth(), retry: false });

    if (health.isPending) {
        return <p role="status">Verificando o serviço…</p>;
    }

    const available = health.isSuccess && health.data;
    return (
        <div className="flex items-center gap-3">
            <p role="status">{available ? 'Serviço disponível.' : 'Serviço indisponível no momento.'}</p>
            {!available && (
                <Button variant="outline" size="sm" onClick={() => void health.refetch()}>
                    Tentar de novo
                </Button>
            )}
        </div>
    );
}
