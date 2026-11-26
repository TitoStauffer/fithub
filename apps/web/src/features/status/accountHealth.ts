import { config } from '@/lib/config';

/** Consulta o health público do account-service (só o status agregado, sem dado de usuário). */
export async function fetchAccountHealth(baseUrl: string = config.accountApiUrl): Promise<boolean> {
    const response = await fetch(`${baseUrl}/actuator/health`);
    if (!response.ok) return false;
    const body = (await response.json()) as { status?: unknown };
    return body.status === 'UP';
}
