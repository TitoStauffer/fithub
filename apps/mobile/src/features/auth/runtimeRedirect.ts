import { makeRedirectUri } from 'expo-auth-session';

/**
 * O retorno do login que o Expo calcula para o app como ele roda agora (DEV-0181): `exp://<IP do PC>:8081` no Expo Go e
 * `fithub://` no build do app. Fora do Expo (nos testes, sem manifesto), o `makeRedirectUri` falha, e vale o do build.
 */
export function runtimeRedirectUri(): string {
    try {
        return makeRedirectUri();
    } catch {
        return 'fithub://';
    }
}
