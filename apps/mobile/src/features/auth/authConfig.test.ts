import { edgeUrlFor, PRODUCTION_REDIRECT_URI, redirectUriFor } from './authConfig';

// DEV-0181: o Expo Go volta do login por exp://<IP do PC>:8081, que só o realm local aceita. O build do app, nunca.
describe('retorno do login', () => {
    it('o build de produção usa sempre fithub://auth, mesmo que o Expo calcule um exp://', () => {
        expect(redirectUriFor(false, 'exp://192.168.0.10:8081')).toBe(PRODUCTION_REDIRECT_URI);
        expect(redirectUriFor(false, 'fithub://')).toBe('fithub://auth');
    });

    it('em desenvolvimento, só o Expo Go usa o retorno do Expo', () => {
        expect(redirectUriFor(true, 'exp://192.168.0.10:8081')).toBe('exp://192.168.0.10:8081');
        expect(redirectUriFor(true, 'fithub://')).toBe(PRODUCTION_REDIRECT_URI);
    });
});

describe('endereço da borda do ambiente local', () => {
    it('no Expo Go em desenvolvimento, é o IP do PC que o Metro usa, na porta da borda', () => {
        expect(edgeUrlFor(true, 'exp://192.168.0.10:8081')).toBe('http://192.168.0.10:8000');
    });

    it('o endereço configurado vale sobre o do Metro, sem a barra do fim', () => {
        expect(edgeUrlFor(true, 'exp://192.168.0.10:8081', 'http://10.0.0.5:9000/')).toBe(
            'http://10.0.0.5:9000',
        );
    });

    it('no build de produção e fora do Expo Go, não há borda local', () => {
        expect(edgeUrlFor(false, 'exp://192.168.0.10:8081')).toBeUndefined();
        expect(edgeUrlFor(true, 'fithub://')).toBeUndefined();
    });
});
