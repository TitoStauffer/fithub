import { NOW, fakeGateway, fakeStore, tokens } from './fakes';
import { InvalidSessionError, MIN_VALIDITY_MS, createSession } from './session';

function setup(storedRefreshToken?: string) {
    const gateway = fakeGateway();
    const store = fakeStore(storedRefreshToken);
    let now = NOW;
    const session = createSession(gateway, store, () => now);
    const statuses: string[] = [];
    session.subscribe((status) => statuses.push(status));
    return { gateway, store, session, statuses, advance: (ms: number) => (now += ms) };
}

describe('retomar a sessão ao abrir o app', () => {
    it('sem nada no cofre, fica sem sessão e não fala com o Keycloak', async () => {
        const { session, gateway } = setup();
        await session.restore();

        expect(session.status()).toBe('signedOut');
        expect(gateway.calls).toEqual([]);
    });

    it('com refresh token no cofre, renova e guarda o refresh token novo', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        await session.restore();

        expect(session.status()).toBe('signedIn');
        expect(gateway.calls).toEqual(['refresh:refresh-guardado']);
        expect(store.value).toBe('refresh-renewed-1');
        await expect(session.getAccessToken()).resolves.toBe('access-renewed-1');
    });

    it('refresh token recusado pelo Keycloak apaga o cofre e leva à tela de entrada', async () => {
        const { session, gateway, store } = setup('refresh-revogado');
        gateway.refreshMode = 'invalid';
        await session.restore();

        expect(session.status()).toBe('signedOut');
        expect(store.value).toBeUndefined();
        expect(store.clears).toBe(1);
    });

    it('Keycloak fora do ar não apaga a sessão guardada, e tentar de novo retoma', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        gateway.refreshMode = 'offline';
        await session.restore();

        expect(session.status()).toBe('unavailable');
        expect(store.value).toBe('refresh-guardado');
        expect(store.clears).toBe(0);

        gateway.refreshMode = 'ok';
        await session.restore();
        expect(session.status()).toBe('signedIn');
    });

    it('cofre ilegível é tratado como sem sessão, sem travar o app', async () => {
        const { session, store } = setup('refresh-guardado');
        store.failLoad = true;
        await session.restore();

        expect(session.status()).toBe('signedOut');
    });
});

describe('entrar e sair', () => {
    it('login guarda só o refresh token no cofre', async () => {
        const { session, store } = setup();
        await session.restore();
        await session.signIn();

        expect(session.status()).toBe('signedIn');
        expect(store.saved).toEqual(['refresh-login']);
        expect(JSON.stringify(store)).not.toContain('access-login');
    });

    it('fechar o navegador sem entrar não muda nada', async () => {
        const { session, gateway, store } = setup();
        await session.restore();
        gateway.signInResult = undefined;
        await session.signIn();

        expect(session.status()).toBe('signedOut');
        expect(store.saved).toEqual([]);
    });

    it('falha no login é repassada e não cria sessão', async () => {
        const { session, gateway, store } = setup();
        await session.restore();
        gateway.signInResult = new Error('O login não foi concluído.');

        await expect(session.signIn()).rejects.toThrow('O login não foi concluído.');
        expect(session.status()).toBe('signedOut');
        expect(store.saved).toEqual([]);
    });

    it('logout revoga a sessão no Keycloak e apaga o que foi guardado', async () => {
        const { session, gateway, store } = setup();
        await session.restore();
        await session.signIn();
        await session.signOut();

        expect(gateway.calls).toContain('signOut:refresh-login');
        expect(store.value).toBeUndefined();
        expect(session.status()).toBe('signedOut');
        await expect(session.getAccessToken()).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it('logout sem rede ainda apaga tudo do aparelho', async () => {
        const { session, gateway, store } = setup();
        await session.restore();
        await session.signIn();
        gateway.signOutFails = true;

        await expect(session.signOut()).resolves.toBeUndefined();
        expect(store.value).toBeUndefined();
        expect(session.status()).toBe('signedOut');
    });
});

describe('access token para as chamadas', () => {
    it('token ainda válido é devolvido sem renovar', async () => {
        const { session, gateway } = setup();
        await session.signIn();

        await expect(session.getAccessToken()).resolves.toBe('access-login');
        expect(gateway.calls).toEqual(['signIn']);
    });

    it('token perto de vencer é renovado antes, e o refresh token novo vai para o cofre', async () => {
        const { session, gateway, store, advance } = setup();
        await session.signIn();
        advance(300_000 - MIN_VALIDITY_MS);

        await expect(session.getAccessToken()).resolves.toBe('access-renewed-1');
        expect(gateway.calls).toEqual(['signIn', 'refresh:refresh-login']);
        expect(store.value).toBe('refresh-renewed-1');
    });

    it('force renova mesmo com o token válido', async () => {
        const { session } = setup();
        await session.signIn();

        await expect(session.getAccessToken({ force: true })).resolves.toBe('access-renewed-1');
    });

    it('pedidos simultâneos fazem uma renovação só', async () => {
        const { session, gateway } = setup();
        await session.signIn();

        const results = await Promise.all([
            session.getAccessToken({ force: true }),
            session.getAccessToken({ force: true }),
            session.getAccessToken({ force: true }),
        ]);

        expect(results).toEqual(['access-renewed-1', 'access-renewed-1', 'access-renewed-1']);
        expect(gateway.calls.filter((call) => call.startsWith('refresh:'))).toHaveLength(1);
    });

    it('refresh recusado na renovação encerra a sessão e apaga o cofre', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        gateway.refreshMode = 'invalid';

        await expect(session.getAccessToken({ force: true })).rejects.toBeInstanceOf(InvalidSessionError);
        expect(session.status()).toBe('signedOut');
        expect(store.value).toBeUndefined();
    });

    it('falha passageira na renovação não encerra a sessão nem apaga o cofre', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        gateway.refreshMode = 'offline';

        await expect(session.getAccessToken({ force: true })).rejects.toThrow('sem rede');
        expect(session.status()).toBe('signedIn');
        expect(store.value).toBe('refresh-login');

        gateway.refreshMode = 'ok';
        await expect(session.getAccessToken({ force: true })).resolves.toBe('access-renewed-1');
    });

    it('sem sessão, não há token', async () => {
        const { session } = setup();
        await session.restore();

        await expect(session.getAccessToken()).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it('reject encerra a sessão local e apaga o cofre', async () => {
        const { session, store } = setup();
        await session.signIn();
        await session.reject();

        expect(session.status()).toBe('signedOut');
        expect(store.value).toBeUndefined();
    });
});

// Code Review do PR #196 (MEDIUM): uma renovação que começou antes do encerramento da sessão não pode
// regravar o token em memória nem o refresh token no cofre quando o Keycloak finalmente responde.
describe('renovação em andamento quando a sessão é encerrada', () => {
    it('logout durante a renovação descarta o resultado: cofre vazio e nenhum token em memória', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        const release = gateway.holdNextRefresh();
        const pending = session.getAccessToken({ force: true });

        await session.signOut();
        release();

        await expect(pending).rejects.toBeInstanceOf(InvalidSessionError);
        expect(store.value).toBeUndefined();
        expect(store.saved).toEqual(['refresh-login']);
        expect(session.status()).toBe('signedOut');
        await expect(session.getAccessToken()).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it('reject durante a renovação também descarta o resultado', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        const release = gateway.holdNextRefresh();
        const pending = session.getAccessToken({ force: true });

        await session.reject();
        release();

        await expect(pending).rejects.toBeInstanceOf(InvalidSessionError);
        expect(store.value).toBeUndefined();
        expect(session.status()).toBe('signedOut');
    });

    it('logout enquanto o app ainda retoma a sessão termina sem sessão', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        const release = gateway.holdNextRefresh();
        const restoring = session.restore();
        // Deixa a retomada ler o cofre e chegar ao Keycloak antes do logout.
        await Promise.resolve();
        await Promise.resolve();

        await session.signOut();
        release();
        await restoring;

        expect(session.status()).toBe('signedOut');
        expect(store.value).toBeUndefined();
    });

    it('depois de um encerramento, um login novo renova normalmente', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        const release = gateway.holdNextRefresh();
        const pending = session.getAccessToken({ force: true }).catch(() => undefined);
        await session.signOut();
        release();
        await pending;

        await session.signIn();
        await expect(session.getAccessToken({ force: true })).resolves.toMatch(/^access-renewed-/);
        expect(store.value).toMatch(/^refresh-renewed-/);
    });
});

// Code Review do PR #196, 2ª rodada (LOW): a conferência vale também depois do cofre, e uma resposta
// atrasada só encerra a sessão em que nasceu.
describe('respostas atrasadas e cofre lento', () => {
    it('logout enquanto o cofre grava a renovação não devolve o token à memória nem ao cofre', async () => {
        const { session, store } = setup();
        await session.signIn();
        const release = store.holdNextSave();
        const pending = session.getAccessToken({ force: true });
        // Deixa a renovação chegar à gravação no cofre.
        await new Promise((resolve) => setImmediate(resolve));

        const leaving = session.signOut();
        release();
        await leaving;

        await expect(pending).rejects.toBeInstanceOf(InvalidSessionError);
        expect(store.value).toBeUndefined();
        expect(session.status()).toBe('signedOut');
        await expect(session.getAccessToken()).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it('resposta atrasada de uma sessão antiga não derruba o login feito depois', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        const release = gateway.holdNextRefresh();
        const stale = session.getAccessToken({ force: true });

        await session.signOut();
        gateway.signInResult = tokens('segundo-login');
        await session.signIn();
        // Só agora o Keycloak responde à renovação da sessão antiga.
        release();

        await expect(stale).rejects.toBeInstanceOf(InvalidSessionError);
        expect(session.status()).toBe('signedIn');
        expect(store.value).toBe('refresh-segundo-login');
        await expect(session.getAccessToken()).resolves.toBe('access-segundo-login');
    });

    it('renovação recusada de uma sessão antiga não derruba o login feito depois', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        const release = gateway.holdNextRefresh();
        const stale = session.getAccessToken({ force: true });

        await session.signOut();
        gateway.signInResult = tokens('segundo-login');
        await session.signIn();
        // O logout revogou a sessão antiga: a renovação dela volta recusada.
        gateway.refreshMode = 'invalid';
        release();

        await expect(stale).rejects.toBeInstanceOf(InvalidSessionError);
        expect(session.status()).toBe('signedIn');
        expect(store.value).toBe('refresh-segundo-login');
    });

    it('gravações e apagamentos no cofre acontecem na ordem em que foram pedidos', async () => {
        const { session, store } = setup();
        const release = store.holdNextSave();
        const entering = session.signIn();
        await new Promise((resolve) => setImmediate(resolve));

        // Sai enquanto o login ainda grava: o apagamento espera a gravação e vence.
        const leaving = session.reject();
        release();
        await leaving;

        await expect(entering).rejects.toBeInstanceOf(InvalidSessionError);
        expect(store.value).toBeUndefined();
        expect(session.status()).toBe('signedOut');
    });
});

// Code Review do PR #196 (LOW): falha do cofre não pode prender o app num estado errado.
describe('cofre com defeito', () => {
    it('falha ao apagar não impede o logout: tenta de novo, encerra a sessão e revoga no Keycloak', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        store.failClears = 1;

        await expect(session.signOut()).resolves.toBeUndefined();

        expect(session.status()).toBe('signedOut');
        expect(store.clears).toBe(2);
        expect(store.value).toBeUndefined();
        expect(gateway.calls).toContain('signOut:refresh-login');
    });

    it('cofre que não apaga de jeito nenhum ainda encerra a sessão e revoga no Keycloak', async () => {
        const { session, gateway, store } = setup();
        await session.signIn();
        store.failClears = 5;

        await expect(session.signOut()).resolves.toBeUndefined();

        expect(session.status()).toBe('signedOut');
        expect(gateway.calls).toContain('signOut:refresh-login');
        await expect(session.getAccessToken()).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it('refresh recusado ao abrir o app com cofre travado não deixa a tela presa em carregando', async () => {
        const { session, gateway, store } = setup('refresh-revogado');
        gateway.refreshMode = 'invalid';
        store.failClears = 5;

        await expect(session.restore()).resolves.toBeUndefined();
        expect(session.status()).toBe('signedOut');
    });

    it('falha ao guardar no login não abre sessão nem deixa token em memória', async () => {
        const { session, store } = setup();
        await session.restore();
        store.failSave = true;

        await expect(session.signIn()).rejects.toThrow('cofre cheio');
        expect(session.status()).toBe('signedOut');
        await expect(session.getAccessToken()).rejects.toBeInstanceOf(InvalidSessionError);
    });
});

it('tokens() dos testes vence em 5 minutos, como o access token do realm', () => {
    expect(tokens('x').expiresAt - NOW).toBe(300_000);
});

// DEV-0148 (achados do Code Review do PR #196): login durante a retomada e logout sem token em memória.
describe('ações durante a retomada da sessão', () => {
    it('login concluído antes da resposta da retomada vale, e o token atrasado é revogado', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        const release = gateway.holdNextRefresh();
        const restoring = session.restore();
        // Deixa a retomada ler o cofre e chegar ao Keycloak.
        await new Promise((resolve) => setImmediate(resolve));
        expect(gateway.calls).toContain('refresh:refresh-guardado');

        gateway.signInResult = tokens('login-novo');
        await session.signIn();
        release();
        await restoring;

        expect(session.status()).toBe('signedIn');
        expect(store.value).toBe('refresh-login-novo');
        await expect(session.getAccessToken()).resolves.toBe('access-login-novo');
        expect(gateway.calls).toContain('signOut:refresh-renewed-1');
    });

    it('logout durante a retomada revoga o refresh token guardado no cofre', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        const release = gateway.holdNextRefresh();
        const restoring = session.restore();
        // Deixa a retomada ler o cofre e chegar ao Keycloak.
        await new Promise((resolve) => setImmediate(resolve));
        expect(gateway.calls).toContain('refresh:refresh-guardado');

        await session.signOut();
        release();
        await restoring;

        expect(gateway.calls).toContain('signOut:refresh-guardado');
        expect(store.value).toBeUndefined();
        expect(session.status()).toBe('signedOut');
    });

    it('logout com o Keycloak fora do ar revoga o refresh token guardado no cofre', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        gateway.refreshMode = 'offline';
        await session.restore();
        expect(session.status()).toBe('unavailable');

        await session.signOut();

        expect(gateway.calls).toContain('signOut:refresh-guardado');
        expect(store.value).toBeUndefined();
    });
});
