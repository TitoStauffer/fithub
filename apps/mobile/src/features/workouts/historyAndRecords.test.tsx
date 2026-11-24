import { render, screen, userEvent, waitFor } from '@testing-library/react-native';

import {
    ApiProblemError,
    type ExerciseRecords,
    type SessionHistoryPage,
    type SessionSummary,
} from '../../lib/api';
import { HistoryScreen } from './HistoryScreen';
import { isoDate, RecordsScreen } from './RecordsScreen';
import type { HistoryApi, SessionApi } from './workoutsApi';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'id-do-aparelho' }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

function summary(index: number, extra: Partial<SessionSummary> = {}): SessionSummary {
    return {
        id: `s-${index}`,
        planId: 'p-1',
        planName: 'ABC',
        divisionPosition: 0,
        divisionLetter: 'A',
        divisionName: 'Pernas',
        startedAt: '2026-11-24T12:00:00Z',
        finishedAt: '2026-11-24T13:00:00Z',
        setCount: 3,
        ...extra,
    };
}

function historyApi(overrides: Partial<HistoryApi> = {}): HistoryApi {
    return {
        history: jest.fn(async (page: number): Promise<SessionHistoryPage> => ({
            items:
                page === 0
                    ? Array.from({ length: 20 }, (_, index) => summary(index))
                    : [
                          summary(20, {
                              divisionLetter: 'B',
                              divisionName: undefined,
                              setCount: 1,
                              feeling: 4,
                          }),
                      ],
            total: 21,
            page,
            size: 20,
        })),
        records: jest.fn(async () => []),
        addRecord: jest.fn(async () => ({ id: 'r-1' })),
        searchExercises: jest.fn(async () => [{ id: 'e-squat', name: 'Agachamento livre' }]),
        ...overrides,
    };
}

const sessionApi: SessionApi = {
    view: jest.fn(async () => ({
        id: 's-0',
        divisionLetter: 'A',
        divisionName: 'Pernas',
        startedAt: '2026-11-24T12:00:00Z',
        comment: 'Joelho bem.',
        feeling: 3,
        items: [
            {
                position: 0,
                exercise: { id: 'e-squat', name: 'Agachamento livre' },
                alternatives: [{ id: 'e-press', name: 'Leg press' }],
                recorded: [
                    { id: 'x-1', setNumber: 1, loadKg: 40, reps: 10, exerciseId: 'e-squat' },
                    { id: 'x-2', setNumber: 2, loadKg: 120.5, reps: 12, exerciseId: 'e-press' },
                ],
            },
        ],
    })),
    record: jest.fn(),
    remove: jest.fn(),
    feedback: jest.fn(),
    finish: jest.fn(),
};

describe('histórico de treinos', () => {
    it('lista os treinos finalizados com paginação', async () => {
        const user = userEvent.setup();
        const api = historyApi();
        await render(<HistoryScreen api={api} sessionApi={sessionApi} onDone={jest.fn()} />);

        expect(await screen.findAllByText('ABC · Treino A — Pernas')).toHaveLength(20);
        expect(screen.getAllByText('3 séries')).toHaveLength(20);
        await user.press(screen.getByRole('button', { name: 'Carregar mais' }));

        expect(api.history).toHaveBeenLastCalledWith(1);
        expect(await screen.findByText('ABC · Treino B')).toBeOnTheScreen();
        expect(screen.getByText('1 série · Difícil')).toBeOnTheScreen();
        // Com tudo carregado, não há mais o que pedir.
        expect(screen.queryByRole('button', { name: 'Carregar mais' })).toBeNull();
    });

    it('o detalhe mostra as séries de cada exercício, o comentário e a sensação', async () => {
        const user = userEvent.setup();
        await render(<HistoryScreen api={historyApi()} sessionApi={sessionApi} onDone={jest.fn()} />);

        const items = await screen.findAllByRole('button', { name: /ABC, Treino A — Pernas/ });
        await user.press(items[0]!);

        expect(await screen.findByText('Série 1: 40 kg × 10')).toBeOnTheScreen();
        expect(screen.getByText('Série 2: 120,5 kg × 12 (Leg press)')).toBeOnTheScreen();
        expect(screen.getByText('Comentário: Joelho bem.')).toBeOnTheScreen();
        expect(screen.getByText('Sensação: Moderado')).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Voltar ao histórico' }));
        expect(await screen.findAllByText('ABC · Treino A — Pernas')).toHaveLength(20);
    });

    it('sem treinos finalizados, avisa', async () => {
        await render(
            <HistoryScreen
                api={historyApi({
                    history: jest.fn(async () => ({ items: [], total: 0, page: 0, size: 20 })),
                })}
                sessionApi={sessionApi}
                onDone={jest.fn()}
            />,
        );

        expect(await screen.findByText('Você ainda não finalizou nenhum treino.')).toBeOnTheScreen();
    });
});

const RECORDS: ExerciseRecords[] = [
    {
        exerciseId: 'e-squat',
        exerciseName: 'Agachamento livre',
        maxLoad: {
            valueKg: 110,
            loadKg: 110,
            reps: 1,
            date: '2026-11-20',
            source: 'MANUAL',
            sourceId: 'r-1',
        },
        estimatedOneRepMax: {
            valueKg: 115.5,
            loadKg: 99,
            reps: 5,
            date: '2026-11-24',
            source: 'SET',
            sourceId: 'x-1',
        },
    },
];

describe('recordes', () => {
    it('mostra por exercício a maior carga e o 1RM com a data e a origem', async () => {
        await render(
            <RecordsScreen api={historyApi({ records: jest.fn(async () => RECORDS) })} onDone={jest.fn()} />,
        );

        expect(await screen.findByText('Agachamento livre')).toBeOnTheScreen();
        expect(
            screen.getByText('Maior carga: 110 kg — 1 × 110 kg em 20/11/2026, lançado à mão'),
        ).toBeOnTheScreen();
        expect(
            screen.getByText('1RM estimado: 115,5 kg — 5 × 99 kg em 24/11/2026, registrada no treino'),
        ).toBeOnTheScreen();
    });

    it('lança o recorde manual e ele aparece marcado como manual', async () => {
        const user = userEvent.setup();
        const records = jest
            .fn<Promise<ExerciseRecords[]>, []>()
            .mockResolvedValueOnce([])
            .mockResolvedValueOnce(RECORDS);
        const api = historyApi({ records });
        await render(<RecordsScreen api={api} now={new Date(2026, 10, 24, 12)} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Lançar recorde' }));
        await user.type(screen.getByLabelText('Buscar exercício'), 'agach');
        await user.press(screen.getByRole('button', { name: 'Buscar' }));
        await user.press(await screen.findByRole('button', { name: 'Escolher Agachamento livre' }));
        await user.type(screen.getByLabelText('Carga (kg)'), '110');
        await user.type(screen.getByLabelText('Repetições'), '1');
        const date = screen.getByLabelText('Data (dd/mm/aaaa)');
        expect(date).toHaveDisplayValue('24/11/2026');
        await user.clear(date);
        await user.type(date, '20/11/2026');
        await user.press(screen.getByRole('button', { name: 'Salvar recorde' }));

        expect(api.addRecord).toHaveBeenCalledWith({
            exerciseId: 'e-squat',
            loadKg: 110,
            reps: 1,
            performedOn: '2026-11-20',
        });
        expect(await screen.findByText('Recorde lançado.')).toBeOnTheScreen();
        expect(await screen.findByText(/lançado à mão/)).toBeOnTheScreen();
    });

    it('a validação da API aparece no campo e o digitado fica', async () => {
        const user = userEvent.setup();
        const api = historyApi({
            addRecord: jest.fn(async () => {
                throw new ApiProblemError(400, 'Um ou mais campos são inválidos.', {
                    performedOn: 'A data não pode ser no futuro.',
                });
            }),
        });
        await render(<RecordsScreen api={api} now={new Date(2026, 10, 24, 12)} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Lançar recorde' }));
        await user.type(screen.getByLabelText('Buscar exercício'), 'agach');
        await user.press(screen.getByRole('button', { name: 'Buscar' }));
        await user.press(await screen.findByRole('button', { name: 'Escolher Agachamento livre' }));
        await user.type(screen.getByLabelText('Carga (kg)'), '100');
        await user.type(screen.getByLabelText('Repetições'), '1');
        await user.press(screen.getByRole('button', { name: 'Salvar recorde' }));

        expect(await screen.findByText('A data não pode ser no futuro.')).toBeOnTheScreen();
        expect(screen.getByLabelText('Carga (kg)')).toHaveDisplayValue('100');
    });

    it('sem exercício ou com a data fora do formato, não envia', async () => {
        const user = userEvent.setup();
        const api = historyApi();
        await render(<RecordsScreen api={api} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Lançar recorde' }));
        const date = screen.getByLabelText('Data (dd/mm/aaaa)');
        await user.clear(date);
        await user.type(date, '2026-11-20');
        await user.press(screen.getByRole('button', { name: 'Salvar recorde' }));

        await waitFor(() => expect(screen.getByText('Escolha o exercício.')).toBeOnTheScreen());
        expect(screen.getByText('Use a data no formato dd/mm/aaaa.')).toBeOnTheScreen();
        expect(api.addRecord).not.toHaveBeenCalled();
        expect(isoDate('20/11/2026')).toBe('2026-11-20');
        expect(isoDate('2026-11-20')).toBeUndefined();
    });
});
