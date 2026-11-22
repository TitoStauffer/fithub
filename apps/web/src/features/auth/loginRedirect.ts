/**
 * Trava contra laço de redirecionamento: se o app acabou de mandar a pessoa ao login e ela voltou sem
 * sessão, não manda de novo sozinho. Guarda só o horário do último envio, nunca token.
 */
const KEY = 'fithub.auth.login-redirect-at';
export const REDIRECT_WINDOW_MS = 60_000;

export function markLoginRedirect(now: number = Date.now()): void {
    window.sessionStorage.setItem(KEY, String(now));
}

export function clearLoginRedirect(): void {
    window.sessionStorage.removeItem(KEY);
}

/** Houve um envio ao login há pouco, de que a pessoa pode estar voltando agora. */
export function cameBackFromLogin(now: number = Date.now()): boolean {
    const at = Number(window.sessionStorage.getItem(KEY));
    return Number.isFinite(at) && at > 0 && now - at >= 0 && now - at < REDIRECT_WINDOW_MS;
}
