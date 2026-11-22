const DEFAULT_ACCOUNT_API_URL = 'http://localhost:8081';

/** Consulta o health público do account-service (só o status agregado, sem dado de usuário). */
export async function fetchAccountHealth(baseUrl: string = accountApiUrl()): Promise<boolean> {
    const response = await fetch(`${baseUrl}/actuator/health`);
    if (!response.ok) return false;
    const body = (await response.json()) as { status?: unknown };
    return body.status === 'UP';
}

function accountApiUrl(): string {
    const configured: unknown = import.meta.env.VITE_ACCOUNT_API_URL;
    return typeof configured === 'string' && configured.length > 0 ? configured : DEFAULT_ACCOUNT_API_URL;
}
