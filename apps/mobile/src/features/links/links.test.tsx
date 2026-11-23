import { render, screen, userEvent } from '@testing-library/react-native';

import { ApiProblemError, type ClientLink, type ClientProfile, type InvitationPreview } from '../../lib/api';
import { ClientArea, type LinkSource } from '../profile/ClientArea';
import type { ProfileApi } from '../profile/profileApi';
import { tokenFromUrl, type LinksApi } from './linksApi';
import { LinksScreen } from './LinksScreen';

jest.mock('../auth/SessionProvider', () => ({ useSession: () => ({ apiFetch: jest.fn() }) }));
jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const TOKEN = 'abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG';

const PROFILE: ClientProfile = {
    name: 'Ana Cliente',
    birthDate: '1990-11-24',
    age: 35,
    gender: 'FEMALE',
    heightCm: 165,
    goal: 'HEALTH',
};

const PREVIEW: InvitationPreview = {
    kind: 'INVITATION',
    professionalName: 'Paula Personal',
    professionalType: 'PERSONAL_TRAINER',
    sharedData: ['PROFILE', 'HEALTH', 'ASSESSMENTS', 'PHOTOS'],
    expiresAt: '2026-11-30T12:00:00Z',
    termsVersion: 'v1',
    termsText: 'Ao aceitar, você autoriza este profissional a ver seus dados.',
};

const profileApi: ProfileApi = {
    fetchProfile: jest.fn(async () => PROFILE),
    saveProfile: jest.fn(),
    fetchHealth: jest.fn(async () => null),
    saveHealth: jest.fn(),
};

function links(overrides: Partial<LinksApi> = {}): LinksApi {
    return {
        lookup: jest.fn(async () => PREVIEW),
        accept: jest.fn(async () => ({ id: 'link-1' })),
        list: jest.fn(async () => []),
        end: jest.fn(async () => ({ id: 'link-1', status: 'ENDED' as const })),
        ...overrides,
    };
}

function source(initialUrl: string | null = null): LinkSource {
    return {
        getInitialURL: async () => initialUrl,
        addEventListener: () => ({ remove: () => undefined }),
    };
}

describe('aceite do convite', () => {
    it('o link do convite abre a tela de aceite com o que será compartilhado e a versão do termo', async () => {
        const api = links();
        await render(
            <ClientArea api={profileApi} linksApi={api} linkSource={source(`fithub://convite/${TOKEN}`)} />,
        );

        expect(await screen.findByText('Paula Personal quer acompanhar você')).toBeOnTheScreen();
        expect(api.lookup).toHaveBeenCalledWith({ token: TOKEN });
        expect(screen.getByText('• Seu perfil de saúde')).toBeOnTheScreen();
        expect(screen.getByText('• Suas fotos de evolução')).toBeOnTheScreen();
        expect(screen.getByText('Termo de consentimento, versão v1')).toBeOnTheScreen();
    });

    it('só aceita com a confirmação explícita, e envia o consentimento com a versão mostrada', async () => {
        const user = userEvent.setup();
        const api = links();
        await render(
            <ClientArea api={profileApi} linksApi={api} linkSource={source(`fithub://convite/${TOKEN}`)} />,
        );

        const acceptButton = await screen.findByRole('button', { name: 'Aceitar' });
        expect(acceptButton).toBeDisabled();
        await user.press(acceptButton);
        expect(api.accept).not.toHaveBeenCalled();

        await user.press(screen.getByRole('checkbox', { name: 'Li e concordo em compartilhar esses dados' }));
        await user.press(screen.getByRole('button', { name: 'Aceitar' }));

        expect(api.accept).toHaveBeenCalledWith({ token: TOKEN, consent: true, termsVersion: 'v1' });
        expect(await screen.findByText('Meus vínculos')).toBeOnTheScreen();
    });

    it('recusar volta sem criar vínculo', async () => {
        const user = userEvent.setup();
        const api = links();
        await render(
            <ClientArea api={profileApi} linksApi={api} linkSource={source(`fithub://convite/${TOKEN}`)} />,
        );

        await user.press(await screen.findByRole('button', { name: 'Recusar' }));

        expect(api.accept).not.toHaveBeenCalled();
        expect(await screen.findByText('Olá, Ana Cliente')).toBeOnTheScreen();
    });

    it('aceita digitando o código do convite ou do profissional', async () => {
        const user = userEvent.setup();
        const api = links();
        await render(<ClientArea api={profileApi} linksApi={api} linkSource={source()} />);

        await user.press(await screen.findByRole('button', { name: 'Aceitar convite' }));
        await user.type(screen.getByLabelText('Código do convite ou do profissional'), 'abcd-2345');
        await user.press(screen.getByRole('button', { name: 'Continuar' }));

        expect(await screen.findByText('Paula Personal quer acompanhar você')).toBeOnTheScreen();
        expect(api.lookup).toHaveBeenCalledWith({ code: 'abcd-2345' });
        await user.press(screen.getByRole('checkbox', { name: 'Li e concordo em compartilhar esses dados' }));
        await user.press(screen.getByRole('button', { name: 'Aceitar' }));
        expect(api.accept).toHaveBeenCalledWith({ code: 'abcd-2345', consent: true, termsVersion: 'v1' });
    });

    it('código desconhecido e convite sem validade mostram a mensagem certa', async () => {
        const user = userEvent.setup();
        const lookup = jest
            .fn<Promise<InvitationPreview>, [{ token: string } | { code: string }]>()
            .mockRejectedValueOnce(new ApiProblemError(404, 'Convite não encontrado.', {}))
            .mockRejectedValueOnce(
                new ApiProblemError(410, 'Este convite não vale mais. Peça um novo ao profissional.', {}),
            );
        await render(<ClientArea api={profileApi} linksApi={links({ lookup })} linkSource={source()} />);
        await user.press(await screen.findByRole('button', { name: 'Aceitar convite' }));
        const field = screen.getByLabelText('Código do convite ou do profissional');

        await user.type(field, 'ZZZZ9999');
        await user.press(screen.getByRole('button', { name: 'Continuar' }));
        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Convite não encontrado. Confira o código e tente de novo.',
        );

        await user.press(screen.getByRole('button', { name: 'Continuar' }));
        expect(
            await screen.findByText('Este convite não vale mais. Peça um novo ao profissional.'),
        ).toBeOnTheScreen();
    });

    it('com um personal ativo, a tela explica que é preciso encerrar o atual', async () => {
        const user = userEvent.setup();
        const accept = jest.fn(async () => {
            throw new ApiProblemError(
                409,
                'Você já tem um personal ativo. Encerre o vínculo atual antes de aceitar outro.',
                {},
            );
        });
        await render(
            <ClientArea
                api={profileApi}
                linksApi={links({ accept })}
                linkSource={source(`fithub://convite/${TOKEN}`)}
            />,
        );

        await user.press(
            await screen.findByRole('checkbox', { name: 'Li e concordo em compartilhar esses dados' }),
        );
        await user.press(screen.getByRole('button', { name: 'Aceitar' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Você já tem um personal ativo. Encerre o vínculo atual antes de aceitar outro.',
        );
        expect(screen.getByText('Paula Personal quer acompanhar você')).toBeOnTheScreen();
    });
});

describe('vínculos', () => {
    const LINKS: ClientLink[] = [
        { id: 'l1', professionalName: 'Paula', professionalType: 'PERSONAL_TRAINER', status: 'ACTIVE' },
        { id: 'l2', professionalName: 'Nina', professionalType: 'NUTRITIONIST', status: 'ENDED' },
    ];

    it('mostra os vínculos ativos e encerrados', async () => {
        await render(<LinksScreen api={links({ list: jest.fn(async () => LINKS) })} onDone={jest.fn()} />);

        expect(await screen.findByText('Personal trainer · Ativo')).toBeOnTheScreen();
        expect(screen.getByText('Nutricionista · Encerrado')).toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Encerrar vínculo com Nina' })).not.toBeOnTheScreen();
    });

    it('encerrar pede confirmação; cancelar não encerra', async () => {
        const user = userEvent.setup();
        const api = links({ list: jest.fn(async () => LINKS) });
        await render(<LinksScreen api={api} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Encerrar vínculo com Paula' }));
        expect(
            screen.getByText('Paula vai perder o acesso aos seus dados na hora. Encerrar mesmo?'),
        ).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Cancelar' }));
        expect(api.end).not.toHaveBeenCalled();

        await user.press(screen.getByRole('button', { name: 'Encerrar vínculo com Paula' }));
        await user.press(screen.getByRole('button', { name: 'Confirmar encerramento' }));
        expect(api.end).toHaveBeenCalledWith('l1');
    });
});

describe('link do convite', () => {
    it('extrai o token só de links do convite do FitHub', () => {
        expect(tokenFromUrl(`fithub://convite/${TOKEN}`)).toBe(TOKEN);
        expect(tokenFromUrl(`fithub://convite/${TOKEN}?origem=email`)).toBe(TOKEN);
        expect(tokenFromUrl(`https://exemplo.com/convite/${TOKEN}`)).toBeUndefined();
        // Prefixo estranho do mesmo tamanho do fithub://convite/: só o esquema do app abre o aceite.
        expect(tokenFromUrl(`https://evil.app/${TOKEN}`)).toBeUndefined();
        expect(tokenFromUrl('fithub://convite/curto')).toBeUndefined();
        expect(tokenFromUrl(null)).toBeUndefined();
    });
});
