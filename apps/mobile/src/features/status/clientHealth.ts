import { clientApiUrl } from '../auth/authConfig';

/** Consulta o health público do client-service (só o status agregado, sem dado do cliente). */
export async function fetchClientHealth(baseUrl: string = clientApiUrl()): Promise<boolean> {
    const response = await fetch(`${baseUrl}/actuator/health`);
    if (!response.ok) return false;
    const body = (await response.json()) as { status?: unknown };
    return body.status === 'UP';
}
