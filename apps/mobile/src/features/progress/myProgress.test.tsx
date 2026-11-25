import { render, screen, userEvent, within } from '@testing-library/react-native';

import { OfflineError, type BodySeries, type DietProgress, type TrainingProgress } from '../../lib/api';
import { MyProgressScreen } from './MyProgressScreen';
import { bodySummaries, periodStart, type ProgressApi } from './progressApi';

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const NOW = new Date(2026, 10, 25, 12);

const TRAINING: TrainingProgress = {
    adherence: { done: 5, expected: 6.5, percent: 76.9, weeks: [] },
    exercises: [
        {
            exerciseId: 'e1',
            name: 'Supino reto',
            points: [
                { date: '2026-11-03', maxLoadKg: 40, volumeKg: 1200 },
                { date: '2026-11-10', maxLoadKg: 42.5, volumeKg: 1275 },
            ],
        },
        {
            exerciseId: 'e2',
            name: 'Agachamento',
            points: [{ date: '2026-11-04', maxLoadKg: 60, volumeKg: 1800 }],
        },
        { exerciseId: 'e3', name: 'Prancha', points: [] },
    ],
};

const DIET: DietProgress = { adherence: { done: 3, prescribed: 8, percent: 37.5, days: [] } };

const BODY: BodySeries = {
    weightKg: [
        { date: '2026-10-01', value: 72.5 },
        { date: '2026-11-01', value: 71 },
    ],
    circumferences: { waist: [{ date: '2026-11-01', value: 70.5 }] },
    bodyFat: [
        {
            origin: 'NAVY',
            points: [
                { date: '2026-10-01', value: 25 },
                { date: '2026-11-01', value: 25.4 },
            ],
        },
        { origin: 'BIOIMPEDANCE', points: [{ date: '2026-11-01', value: 24.8 }] },
    ],
};

function fakeApi(overrides: Partial<ProgressApi> = {}): ProgressApi {
    return {
        training: jest.fn(async () => TRAINING),
        diet: jest.fn(async () => DIET),
        body: jest.fn(async () => BODY),
        ...overrides,
    };
}

function renderScreen(api: ProgressApi, onRecords = jest.fn()) {
    return render(<MyProgressScreen api={api} now={NOW} onRecords={onRecords} onDone={jest.fn()} />);
}

describe('Minha evolução (DEV-0096)', () => {
    it('mostra o treino, a dieta e o corpo, com o atalho para os recordes', async () => {
        const user = userEvent.setup();
        const onRecords = jest.fn();
        await renderScreen(fakeApi(), onRecords);

        expect(await screen.findByText('Aderência: 5 de 6,5 treinos esperados (76,9%)')).toBeOnTheScreen();
        // O exercício sem treino no período não aparece na escolha.
        const exercise = screen.getByLabelText('Exercício');
        expect(
            within(exercise)
                .getAllByRole('checkbox')
                .map((option) => option.props.accessibilityLabel),
        ).toEqual(['Supino reto', 'Agachamento']);
        expect(screen.getByText('10/11/2026: 42,5 kg, volume 1275 kg')).toBeOnTheScreen();
        await user.press(screen.getByRole('checkbox', { name: 'Agachamento' }));
        expect(screen.getByText('04/11/2026: 60 kg, volume 1800 kg')).toBeOnTheScreen();
        expect(screen.queryByText('10/11/2026: 42,5 kg, volume 1275 kg')).toBeNull();

        expect(await screen.findByText('Aderência: 3 de 8 refeições (37,5%)')).toBeOnTheScreen();

        expect(await screen.findByText('Peso: 72,5 kg → 71 kg (−1,5 kg)')).toBeOnTheScreen();
        expect(screen.getByText('Cintura: 70,5 cm (uma medição)')).toBeOnTheScreen();
        // Cada método de % de gordura tem a sua linha e a sua variação (BR-12).
        expect(screen.getByText('% de gordura (Marinha): 25 % → 25,4 % (+0,4 %)')).toBeOnTheScreen();
        expect(screen.getByText('% de gordura (Bioimpedância): 24,8 % (uma medição)')).toBeOnTheScreen();

        await user.press(screen.getByRole('button', { name: 'Ver recordes' }));
        expect(onRecords).toHaveBeenCalled();
    });

    it('troca o período pedindo o treino e a dieta do novo início', async () => {
        const user = userEvent.setup();
        const api = fakeApi();
        await renderScreen(api);
        await screen.findByText('Aderência: 5 de 6,5 treinos esperados (76,9%)');
        expect(api.training).toHaveBeenLastCalledWith('2026-10-29');

        await user.press(screen.getByRole('checkbox', { name: '12 semanas' }));
        await screen.findByText('Aderência: 5 de 6,5 treinos esperados (76,9%)');
        expect(api.training).toHaveBeenLastCalledWith('2026-09-03');
        expect(api.diet).toHaveBeenLastCalledWith('2026-09-03');
        // O corpo é de todo o histórico: não recarrega com o período.
        expect(api.body).toHaveBeenCalledTimes(1);
    });

    it('sem ficha, sem plano e sem avaliação, cada bloco diz o que falta', async () => {
        await renderScreen(
            fakeApi({
                training: jest.fn(async () => ({
                    adherence: { done: 0, expected: 0, weeks: [] },
                    exercises: [],
                })),
                diet: jest.fn(async () => ({ adherence: { done: 0, prescribed: 0, days: [] } })),
                body: jest.fn(async () => ({ weightKg: [], circumferences: {}, bodyFat: [] })),
            }),
        );

        expect(
            await screen.findByText('Sem ficha no período. A aderência aparece com a ficha publicada.'),
        ).toBeOnTheScreen();
        expect(screen.getByText('Nenhum treino finalizado no período.')).toBeOnTheScreen();
        expect(
            await screen.findByText(
                'Sem plano alimentar no período. A aderência aparece com o plano publicado.',
            ),
        ).toBeOnTheScreen();
        expect(
            await screen.findByText('Nenhuma avaliação ainda. Faça a avaliação online para acompanhar.'),
        ).toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Tentar de novo' })).toBeNull();
    });

    it('sem conexão, avisa no bloco e deixa tentar de novo', async () => {
        const user = userEvent.setup();
        const diet = jest
            .fn<Promise<DietProgress>, [string]>()
            .mockRejectedValueOnce(new OfflineError())
            .mockResolvedValue(DIET);
        await renderScreen(fakeApi({ diet }));

        expect(
            await screen.findByText('Sem conexão com o FitHub. Confira a internet e tente de novo.'),
        ).toBeOnTheScreen();
        // O erro da dieta não derruba os outros blocos.
        expect(await screen.findByText('Aderência: 5 de 6,5 treinos esperados (76,9%)')).toBeOnTheScreen();

        await user.press(screen.getByRole('button', { name: 'Tentar de novo' }));
        expect(await screen.findByText('Aderência: 3 de 8 refeições (37,5%)')).toBeOnTheScreen();
        expect(
            screen.queryByText('Sem conexão com o FitHub. Confira a internet e tente de novo.'),
        ).toBeNull();
    });

    it('o período começa no dia certo e a medida sem valor não aparece', () => {
        expect(periodStart(NOW, 4)).toBe('2026-10-29');
        expect(periodStart(NOW, 8)).toBe('2026-10-01');
        expect(
            bodySummaries({
                weightKg: [
                    { date: '2026-10-01', value: 70 },
                    { date: '2026-11-01', value: 70 },
                ],
                circumferences: { hip: [{ date: '2026-10-01' }] },
                bodyFat: [],
            }),
        ).toEqual([{ label: 'Peso', text: '70 kg → 70 kg (0 kg)' }]);
    });
});
