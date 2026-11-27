import { render, screen, userEvent } from '@testing-library/react-native';

import type { ClientExport, ClientProfile, NutritionExport, TrainingExport } from '../../lib/api';
import { ClientArea, type LinkSource } from '../profile/ClientArea';
import type { ProfileApi } from '../profile/profileApi';
import { collectExport, type DataExportApi } from './dataExport';
import { DownloadDataScreen } from './DownloadDataScreen';

jest.mock('../auth/SessionProvider', () => ({ useSession: () => ({ apiFetch: jest.fn() }) }));
jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const PROFILE_EXPORT = { exportedAt: '2026-11-26T10:00:00Z', assessments: [], links: [], healthReads: [] };
const TRAINING_EXPORT = {
    exportedAt: '2026-11-26T10:00:00Z',
    plans: [],
    sessions: [],
    records: [],
    restrictions: [],
};
const NUTRITION_EXPORT = { exportedAt: '2026-11-26T10:00:00Z', planVersions: [], logs: [], restrictions: [] };

function exportApi(overrides: Partial<DataExportApi> = {}): DataExportApi {
    return {
        profile: jest.fn(async () => PROFILE_EXPORT as ClientExport),
        training: jest.fn(async () => TRAINING_EXPORT as TrainingExport),
        nutrition: jest.fn(async () => NUTRITION_EXPORT as NutritionExport),
        ...overrides,
    };
}

const failing = () => Promise.reject(new Error('fora do ar'));

describe('collectExport', () => {
    it('junta as três exportações com a data em que o arquivo foi gerado', async () => {
        const result = await collectExport(exportApi(), () => new Date('2026-11-26T12:00:00Z'));

        expect(result.complete).toBe(true);
        if (!result.complete) return;
        expect(JSON.parse(result.json)).toEqual({
            exportedAt: '2026-11-26T12:00:00.000Z',
            profile: PROFILE_EXPORT,
            training: TRAINING_EXPORT,
            nutrition: NUTRITION_EXPORT,
        });
    });

    it('com uma parte fora do ar, não entrega o arquivo e diz qual faltou', async () => {
        expect(await collectExport(exportApi({ training: failing }))).toEqual({
            complete: false,
            missing: ['training'],
        });
        expect(await collectExport(exportApi({ profile: failing, nutrition: failing }))).toEqual({
            complete: false,
            missing: ['profile', 'nutrition'],
        });
    });
});

describe('DownloadDataScreen', () => {
    it('avisa que as fotos vêm por link de 5 minutos e entrega o JSON pelo compartilhamento', async () => {
        const user = userEvent.setup();
        const share = jest.fn(async () => true);
        await render(<DownloadDataScreen api={exportApi()} share={share} onDone={jest.fn()} />);

        expect(screen.getByText(/link que vale 5 minutos/)).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Baixar meus dados' }));

        expect(share).toHaveBeenCalledTimes(1);
        const [title, message] = share.mock.calls[0] as unknown as [string, string];
        expect(title).toBe('Meus dados no FitHub');
        expect(JSON.parse(message)).toMatchObject({ profile: PROFILE_EXPORT, nutrition: NUTRITION_EXPORT });
        expect(await screen.findByText('Pronto: seus dados foram entregues.')).toBeOnTheScreen();
    });

    it('com uma parte fora do ar, diz qual e não abre o compartilhamento', async () => {
        const user = userEvent.setup();
        const share = jest.fn(async () => true);
        await render(
            <DownloadDataScreen api={exportApi({ nutrition: failing })} share={share} onDone={jest.fn()} />,
        );

        await user.press(screen.getByRole('button', { name: 'Baixar meus dados' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Não foi possível baixar: alimentação. Nada foi entregue; tente de novo.',
        );
        expect(share).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: 'Baixar meus dados' })).toBeEnabled();
    });
});

describe('DownloadDataScreen sem compartilhar', () => {
    it('fechar o compartilhamento sem escolher nada não diz que os dados foram entregues', async () => {
        const user = userEvent.setup();
        const share = jest.fn(async () => false);
        await render(<DownloadDataScreen api={exportApi()} share={share} onDone={jest.fn()} />);

        await user.press(screen.getByRole('button', { name: 'Baixar meus dados' }));

        expect(share).toHaveBeenCalledTimes(1);
        expect(screen.queryByText('Pronto: seus dados foram entregues.')).toBeNull();
        expect(screen.getByRole('button', { name: 'Baixar meus dados' })).toBeEnabled();
    });
});

describe('ClientArea', () => {
    const PROFILE: ClientProfile = {
        name: 'Ana Cliente',
        birthDate: '1990-11-24',
        age: 35,
        gender: 'FEMALE',
        heightCm: 165,
        goal: 'HEALTH',
    };
    const profileApi: ProfileApi = {
        fetchProfile: jest.fn(async () => PROFILE),
        saveProfile: jest.fn(),
        fetchHealthTerms: jest.fn(async () => ({ termsVersion: 'v1', termsText: 'termo', accepted: true })),
        fetchHealth: jest.fn(async () => null),
        saveHealth: jest.fn(),
    };
    const noLinks: LinkSource = {
        getInitialURL: async () => null,
        addEventListener: () => ({ remove: () => undefined }),
    };

    it('leva da tela inicial ao Baixar meus dados', async () => {
        const user = userEvent.setup();
        const share = jest.fn(async () => true);
        await render(
            <ClientArea
                api={profileApi}
                linkSource={noLinks}
                dataExportApi={exportApi()}
                shareText={share}
            />,
        );

        await user.press(await screen.findByRole('button', { name: 'Baixar meus dados' }));
        await user.press(screen.getByRole('button', { name: 'Baixar meus dados' }));

        expect(share).toHaveBeenCalledTimes(1);
    });
});
