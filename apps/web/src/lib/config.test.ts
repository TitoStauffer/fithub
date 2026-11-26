import { describe, expect, it } from 'vitest';

import { resolveUrl } from './config';

// Em produção a web, as APIs e o Keycloak ficam no mesmo domínio, configurados por caminho (DEV-0176).
describe('resolveUrl', () => {
    it('resolve um caminho na origem da página', () => {
        expect(resolveUrl('/api/account', 'https://fithub.example')).toBe(
            'https://fithub.example/api/account',
        );
        expect(resolveUrl('/auth', 'https://fithub.example')).toBe('https://fithub.example/auth');
    });

    it('usa a origem da página quando nenhuma é informada', () => {
        expect(resolveUrl('/api/client')).toBe(`${window.location.origin}/api/client`);
    });

    it('não termina com barra, para os caminhos das chamadas se juntarem sem barra dupla', () => {
        expect(resolveUrl('/api/training/', 'https://fithub.example')).toBe(
            'https://fithub.example/api/training',
        );
    });

    it('mantém um endereço completo, como o do desenvolvimento', () => {
        expect(resolveUrl('http://localhost:8081', 'https://fithub.example')).toBe('http://localhost:8081');
    });
});
