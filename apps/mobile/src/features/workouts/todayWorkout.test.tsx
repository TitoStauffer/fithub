import { render, screen, userEvent, waitFor } from '@testing-library/react-native';

import { OfflineError, type ActivePlan, type ClientWorkoutItem } from '../../lib/api';
import { TodayWorkoutScreen } from './TodayWorkoutScreen';
import { formatKg, setsAndReps, type WorkoutsApi } from './workoutsApi';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'id-do-aparelho' }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const squat: ClientWorkoutItem = {
    exercise: {
        id: 'e-squat',
        name: 'Agachamento livre',
        mediaUrl: 'https://wger.de/media/agachamento.mp4',
        attribution: {
            source: 'wger',
            license: 'CC-BY-SA 4',
            author: 'wger.de',
            mediaLicense: 'CC-BY-SA 3',
            mediaAuthor: 'Everkinetic',
        },
    },
    sets: 4,
    repsMin: 8,
    repsMax: 12,
    loadKg: 42.5,
    restSeconds: 90,
    technique: 'DROP_SET',
    note: 'Desça até 90 graus.',
    alternatives: [{ id: 'e-press', name: 'Leg press' }],
};

const bench: ClientWorkoutItem = {
    exercise: { id: 'e-bench', name: 'Supino reto' },
    sets: 3,
    repsMin: 10,
    repsMax: 10,
    loadKg: null,
    restSeconds: null,
    technique: null,
    note: null,
    alternatives: [],
};

function abc(next: number, extra: Partial<ActivePlan> = {}): ActivePlan {
    return {
        planId: 'p-1',
        name: 'ABC',
        version: 1,
        startsOn: '2026-11-24',
        expiresOn: '2027-01-19',
        expired: false,
        nextDivision: next,
        nextDivisionLetter: ['A', 'B', 'C'][next],
        divisions: [
            { position: 0, letter: 'A', name: 'Pernas', items: [squat] },
            { position: 1, letter: 'B', name: 'Peito', items: [bench] },
            { position: 2, letter: 'C', name: null, items: [squat] },
        ],
        ...extra,
    };
}

function api(overrides: Partial<WorkoutsApi> = {}): WorkoutsApi {
    return {
        activePlans: jest.fn(async () => [abc(0)]),
        start: jest.fn(async (planId: string, sessionId: string) => ({
            id: sessionId,
            planId,
            divisionPosition: 0,
        })),
        ...overrides,
    };
}

describe('treino de hoje', () => {
    it('mostra o próximo treino com a prescrição e depois de finalizar A mostra B', async () => {
        const workouts = api({
            activePlans: jest
                .fn<Promise<ActivePlan[]>, []>()
                .mockResolvedValueOnce([abc(0)])
                .mockResolvedValueOnce([abc(1)]),
        });
        const view = await render(<TodayWorkoutScreen api={workouts} onDone={jest.fn()} />);

        expect(await screen.findByText('Próximo: Treino A — Pernas')).toBeOnTheScreen();
        expect(screen.getByText('Agachamento livre')).toBeOnTheScreen();
        expect(
            screen.getByText('4 × 8–12 · carga sugerida 42,5 kg · descanso 90 s · Drop-set'),
        ).toBeOnTheScreen();
        expect(screen.getByText('Observação: Desça até 90 graus.')).toBeOnTheScreen();
        expect(screen.getByText('Alternativas: Leg press')).toBeOnTheScreen();
        expect(screen.queryByText('Supino reto')).toBeNull();

        // Depois de finalizar A, a API devolve B como próximo.
        await view.unmount();
        await render(<TodayWorkoutScreen api={workouts} onDone={jest.fn()} />);
        expect(await screen.findByText('Próximo: Treino B — Peito')).toBeOnTheScreen();
        expect(screen.getByText('3 × 10')).toBeOnTheScreen();
    });

    it('abre a mídia pelo link e mostra o crédito do exercício importado', async () => {
        const user = userEvent.setup();
        const openUrl = jest.fn(async () => true);
        await render(<TodayWorkoutScreen api={api()} openUrl={openUrl} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Ver vídeo de Agachamento livre' }));
        expect(openUrl).toHaveBeenCalledWith('https://wger.de/media/agachamento.mp4');
        expect(
            screen.getByText('Fonte: wger (CC-BY-SA 4), por wger.de. Mídia: Everkinetic (CC-BY-SA 3).'),
        ).toBeOnTheScreen();
    });

    it('começa o treino com o id do aparelho e passa a mostrar continuar', async () => {
        const user = userEvent.setup();
        const workouts = api();
        const onOpenSession = jest.fn();
        await render(
            <TodayWorkoutScreen
                api={workouts}
                newId={() => 's-1'}
                onOpenSession={onOpenSession}
                onDone={jest.fn()}
            />,
        );

        await user.press(await screen.findByRole('button', { name: 'Começar treino' }));
        expect(workouts.start).toHaveBeenCalledWith('p-1', 's-1');
        expect(onOpenSession).toHaveBeenCalledWith('s-1', 'p-1');
        await user.press(await screen.findByRole('button', { name: 'Continuar treino' }));
        expect(onOpenSession).toHaveBeenCalledTimes(2);
    });

    it('com sessão aberta mostra continuar, e a ficha própria aparece junto com a do personal', async () => {
        const own = abc(2, { planId: 'p-own', name: 'Minha ficha' });
        await render(
            <TodayWorkoutScreen
                api={api({ activePlans: jest.fn(async () => [abc(0, { openSessionId: 's-9' }), own]) })}
                onDone={jest.fn()}
            />,
        );

        expect(await screen.findByText('Minha ficha')).toBeOnTheScreen();
        expect(screen.getByText('Próximo: Treino C')).toBeOnTheScreen();
        expect(screen.getByRole('button', { name: 'Continuar treino' })).toBeOnTheScreen();
        expect(screen.getByRole('button', { name: 'Começar treino' })).toBeOnTheScreen();
    });

    it('ficha vencida mostra o aviso e o treino segue disponível', async () => {
        await render(
            <TodayWorkoutScreen
                api={api({
                    activePlans: jest.fn(async () => [abc(0, { expired: true, expiresOn: '2026-11-01' })]),
                })}
                onDone={jest.fn()}
            />,
        );

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Esta ficha venceu em 01/11/2026. Você continua treinando nela; peça a nova ao seu personal.',
        );
        expect(screen.getByRole('button', { name: 'Começar treino' })).toBeOnTheScreen();
    });

    it('sem ficha mostra o convite', async () => {
        await render(
            <TodayWorkoutScreen api={api({ activePlans: jest.fn(async () => []) })} onDone={jest.fn()} />,
        );

        expect(await screen.findByText(/Você ainda não tem ficha de treino/)).toBeOnTheScreen();
    });

    it('erro de conexão avisa sem apagar a tela', async () => {
        const user = userEvent.setup();
        const workouts = api({
            start: jest.fn(async () => {
                throw new OfflineError();
            }),
        });
        await render(<TodayWorkoutScreen api={workouts} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Começar treino' }));
        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Sem conexão com o FitHub. Confira a internet e tente de novo.',
        );
        expect(screen.getByText('Próximo: Treino A — Pernas')).toBeOnTheScreen();
    });

    it('sem conexão ao abrir avisa e tenta de novo', async () => {
        const user = userEvent.setup();
        const activePlans = jest
            .fn<Promise<ActivePlan[]>, []>()
            .mockRejectedValueOnce(new OfflineError())
            .mockResolvedValueOnce([abc(0)]);
        await render(<TodayWorkoutScreen api={api({ activePlans })} onDone={jest.fn()} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(/Sem conexão com o FitHub/);
        await user.press(screen.getByRole('button', { name: 'Tentar de novo' }));
        await waitFor(() => expect(screen.getByText('Próximo: Treino A — Pernas')).toBeOnTheScreen());
    });
});

describe('formatação da prescrição', () => {
    it('séries e repetições fixas ou em faixa, e carga com vírgula', () => {
        expect(setsAndReps(bench)).toBe('3 × 10');
        expect(setsAndReps(squat)).toBe('4 × 8–12');
        expect(formatKg(42.5)).toBe('42,5 kg');
        expect(formatKg(40)).toBe('40 kg');
    });
});
