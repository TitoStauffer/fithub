import { render, screen, userEvent, waitFor } from '@testing-library/react-native';

import { OfflineError, type ActivePlan, type RecordedSet, type SessionView } from '../../lib/api';
import { ClientArea } from '../profile/ClientArea';
import type { ProfileApi } from '../profile/profileApi';
import { prefill } from './sessionForm';
import type { SessionApi, WorkoutsApi } from './workoutsApi';
import { WorkoutSessionScreen } from './WorkoutSessionScreen';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'id-do-aparelho' }));
jest.mock('../auth/SessionProvider', () => ({ useSession: () => ({ apiFetch: jest.fn() }) }));
jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const SESSION = 's-1';

function view(
    extra: Partial<SessionView> = {},
    item: Partial<NonNullable<SessionView['items']>[number]> = {},
): SessionView {
    return {
        id: SESSION,
        planId: 'p-1',
        divisionLetter: 'A',
        divisionName: 'Pernas',
        divisionPosition: 0,
        versionNumber: 1,
        correctable: true,
        comment: null,
        feeling: null,
        items: [
            {
                position: 0,
                exercise: { id: 'e-squat', name: 'Agachamento livre' },
                alternatives: [{ id: 'e-press', name: 'Leg press' }],
                sets: 3,
                repsMin: 10,
                repsMax: 10,
                loadKg: 40,
                recorded: [],
                last: [],
                alternativesLast: [
                    { exerciseId: 'e-press', sets: [{ setNumber: 1, loadKg: 120, reps: 12 }] },
                ],
                ...item,
            },
        ],
        ...extra,
    };
}

function api(overrides: Partial<SessionApi> = {}, session: SessionView = view()): SessionApi {
    return {
        view: jest.fn(async () => session),
        record: jest.fn(async (_session: string, setId: string, body) => ({
            id: setId,
            setNumber: body.setNumber,
            loadKg: body.loadKg,
            reps: body.reps,
            exerciseId: body.exerciseId ?? 'e-squat',
        })),
        remove: jest.fn(async () => undefined),
        feedback: jest.fn(async () => session),
        finish: jest.fn(async () => ({ id: SESSION, finishedAt: '2026-11-24T13:00:00Z' })),
        ...overrides,
    };
}

async function renderScreen(
    sessions: SessionApi,
    props: Partial<Parameters<typeof WorkoutSessionScreen>[0]> = {},
) {
    let next = 0;
    await render(
        <WorkoutSessionScreen
            api={sessions}
            sessionId={SESSION}
            newId={() => `set-${++next}`}
            onFinished={jest.fn()}
            onBack={jest.fn()}
            {...props}
        />,
    );
}

describe('registro por série', () => {
    it('3x10 a 40 kg: confirmar sem alterar registra 10 repetições e 40 kg', async () => {
        const user = userEvent.setup();
        const sessions = api();
        await renderScreen(sessions);

        expect(await screen.findByLabelText('Carga (kg) de Agachamento livre, série 1')).toHaveDisplayValue(
            '40',
        );
        expect(screen.getByLabelText('Repetições de Agachamento livre, série 1')).toHaveDisplayValue('10');
        await user.press(screen.getByRole('button', { name: 'Confirmar Agachamento livre, série 1' }));

        expect(sessions.record).toHaveBeenCalledWith(SESSION, 'set-1', {
            itemPosition: 0,
            setNumber: 1,
            loadKg: 40,
            reps: 10,
            exerciseId: null,
        });
        expect(await screen.findByText('Série 1 — registrada')).toBeOnTheScreen();
        expect(screen.getByRole('button', { name: 'Corrigir Agachamento livre, série 1' })).toBeOnTheScreen();
    });

    it('toque duplo usa o mesmo id da série e não gera dois registros', async () => {
        const user = userEvent.setup();
        let release: (set: RecordedSet) => void = () => undefined;
        const record = jest.fn(
            () =>
                new Promise<RecordedSet>((resolve) => {
                    release = resolve;
                }),
        );
        const newId = jest.fn(() => 'set-unico');
        await renderScreen(api({ record }), { newId });

        const button = await screen.findByRole('button', { name: 'Confirmar Agachamento livre, série 1' });
        await user.press(button);
        await user.press(button);
        release({ id: 'set-unico', setNumber: 1, loadKg: 40, reps: 10 });

        await screen.findByText('Série 1 — registrada');
        expect(newId).toHaveBeenCalledTimes(1);
        for (const call of record.mock.calls as unknown as [string, string][]) {
            expect(call[1]).toBe('set-unico');
        }
    });

    it('ajusta carga e repetições antes de confirmar', async () => {
        const user = userEvent.setup();
        const sessions = api();
        await renderScreen(sessions);

        const load = await screen.findByLabelText('Carga (kg) de Agachamento livre, série 2');
        await user.clear(load);
        await user.type(load, '42,5');
        const reps = screen.getByLabelText('Repetições de Agachamento livre, série 2');
        await user.clear(reps);
        await user.type(reps, '8');
        await user.press(screen.getByRole('button', { name: 'Confirmar Agachamento livre, série 2' }));

        expect(sessions.record).toHaveBeenCalledWith(
            SESSION,
            'set-1',
            expect.objectContaining({ setNumber: 2, loadKg: 42.5, reps: 8 }),
        );
    });

    it('troca pela alternativa: as próximas séries usam o último treino dela e gravam o exercício feito', async () => {
        const user = userEvent.setup();
        const sessions = api();
        await renderScreen(sessions);

        await user.press(await screen.findByRole('button', { name: 'Trocar por Leg press' }));
        expect(screen.getByLabelText('Carga (kg) de Leg press, série 1')).toHaveDisplayValue('120');
        expect(screen.getByLabelText('Repetições de Leg press, série 1')).toHaveDisplayValue('12');
        await user.press(screen.getByRole('button', { name: 'Confirmar Leg press, série 1' }));

        expect(sessions.record).toHaveBeenCalledWith(
            SESSION,
            'set-1',
            expect.objectContaining({ exerciseId: 'e-press', loadKg: 120, reps: 12 }),
        );
        expect(screen.getByRole('button', { name: 'Voltar para Agachamento livre' })).toBeOnTheScreen();
    });

    it('comenta, dá a sensação e finaliza', async () => {
        const user = userEvent.setup();
        const sessions = api();
        const onFinished = jest.fn();
        await renderScreen(sessions, { onFinished });

        await user.type(await screen.findByLabelText('Comentário do treino'), 'Joelho bem.');
        await user.press(screen.getByRole('checkbox', { name: 'Difícil' }));
        await user.press(screen.getByRole('button', { name: 'Finalizar treino' }));

        expect(sessions.feedback).toHaveBeenCalledWith(SESSION, { comment: 'Joelho bem.', feeling: 4 });
        expect(sessions.finish).toHaveBeenCalledWith(SESSION);
        expect(onFinished).toHaveBeenCalled();
    });

    it('sem conexão avisa e mantém os valores digitados; o reenvio usa o mesmo id', async () => {
        const user = userEvent.setup();
        const record = jest
            .fn<Promise<RecordedSet>, [string, string, unknown]>()
            .mockRejectedValueOnce(new OfflineError())
            .mockResolvedValueOnce({ id: 'set-1', setNumber: 1, loadKg: 50, reps: 10 });
        await renderScreen(api({ record }));

        const load = await screen.findByLabelText('Carga (kg) de Agachamento livre, série 1');
        await user.clear(load);
        await user.type(load, '50');
        await user.press(screen.getByRole('button', { name: 'Confirmar Agachamento livre, série 1' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(/Sem conexão com o FitHub/);
        expect(screen.getByLabelText('Carga (kg) de Agachamento livre, série 1')).toHaveDisplayValue('50');
        await user.press(screen.getByRole('button', { name: 'Confirmar Agachamento livre, série 1' }));
        await screen.findByText('Série 1 — registrada');
        expect(record.mock.calls.map((call) => call[1])).toEqual(['set-1', 'set-1']);
    });

    it('corrige e apaga no mesmo dia', async () => {
        const user = userEvent.setup();
        const recorded = { id: 'set-old', setNumber: 1, loadKg: 40, reps: 10, exerciseId: 'e-squat' };
        const sessions = api({}, view({}, { recorded: [recorded] }));
        await renderScreen(sessions);

        const load = await screen.findByLabelText('Carga (kg) de Agachamento livre, série 1');
        await user.clear(load);
        await user.type(load, '45');
        await user.press(screen.getByRole('button', { name: 'Corrigir Agachamento livre, série 1' }));
        expect(sessions.record).toHaveBeenCalledWith(
            SESSION,
            'set-old',
            expect.objectContaining({ loadKg: 45 }),
        );

        await user.press(screen.getByRole('button', { name: 'Apagar Agachamento livre, série 1' }));
        expect(sessions.remove).toHaveBeenCalledWith(SESSION, 'set-old');
        await waitFor(() => expect(screen.queryByText('Série 1 — registrada')).toBeNull());
    });

    it('no dia seguinte avisa que já não dá para corrigir', async () => {
        await renderScreen(
            api(
                {},
                view({ correctable: false }, { recorded: [{ id: 'x', setNumber: 1, loadKg: 40, reps: 10 }] }),
            ),
        );

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Este treino é de outro dia: já não dá para registrar nem corrigir as séries.',
        );
        expect(screen.queryByRole('button', { name: /Confirmar|Corrigir|Apagar|Finalizar/ })).toBeNull();
    });
});

describe('pré-preenchimento', () => {
    it('vem do último treino; sem ele, do prescrito com o mínimo da faixa', () => {
        const item = view().items![0]!;
        expect(prefill({ ...item, repsMin: 8, repsMax: 12, loadKg: null }, 1, 'e-squat')).toEqual({
            load: '0',
            reps: '8',
        });
        expect(prefill({ ...item, last: [{ setNumber: 1, loadKg: 42.5, reps: 9 }] }, 2, 'e-squat')).toEqual({
            load: '42,5',
            reps: '9',
        });
    });
});

describe('do treino de hoje ao próximo treino', () => {
    it('começa, finaliza e volta mostrando o treino seguinte', async () => {
        const user = userEvent.setup();
        const plan = (next: number): ActivePlan => ({
            planId: 'p-1',
            name: 'AB',
            nextDivision: next,
            nextDivisionLetter: ['A', 'B'][next],
            divisions: [
                { position: 0, letter: 'A', name: 'Pernas', items: [] },
                { position: 1, letter: 'B', name: 'Peito', items: [] },
            ],
        });
        const workouts: WorkoutsApi = {
            activePlans: jest
                .fn<Promise<ActivePlan[]>, []>()
                .mockResolvedValueOnce([plan(0)])
                .mockResolvedValueOnce([plan(1)]),
            start: jest.fn(async (planId: string, sessionId: string) => ({ id: sessionId, planId })),
        };
        const profile: ProfileApi = {
            fetchProfile: jest.fn(async () => ({
                name: 'Ana Cliente',
                birthDate: '1990-11-24',
                age: 35,
                gender: 'FEMALE' as const,
                heightCm: 165,
                goal: 'HEALTH' as const,
            })),
            saveProfile: jest.fn(),
            fetchHealth: jest.fn(async () => null),
            saveHealth: jest.fn(),
        };
        const sessions = api();
        await render(
            <ClientArea
                api={profile}
                workoutsApi={workouts}
                sessionApi={sessions}
                linkSource={{
                    getInitialURL: async () => null,
                    addEventListener: () => ({ remove: () => undefined }),
                }}
            />,
        );

        await user.press(await screen.findByRole('button', { name: 'Treino de hoje' }));
        await user.press(await screen.findByRole('button', { name: 'Começar treino' }));
        await user.press(await screen.findByRole('button', { name: 'Finalizar treino' }));

        expect(sessions.view).toHaveBeenCalledWith('id-do-aparelho');
        expect(sessions.finish).toHaveBeenCalledWith('id-do-aparelho');
        expect(await screen.findByText('Próximo: Treino B — Peito')).toBeOnTheScreen();
    });
});
