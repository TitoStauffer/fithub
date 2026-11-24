import { render, screen, userEvent, waitFor, within } from '@testing-library/react-native';

import { ApiProblemError, type ActivePlan, type WorkoutPlan, type WorkoutPlanInput } from '../../lib/api';
import { ClientArea } from '../profile/ClientArea';
import type { ProfileApi } from '../profile/profileApi';
import type { OwnPlanApi } from './ownPlanForm';
import { OwnPlanScreen } from './OwnPlanScreen';
import type { WorkoutsApi } from './workoutsApi';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'id-do-aparelho' }));
jest.mock('../auth/SessionProvider', () => ({ useSession: () => ({ apiFetch: jest.fn() }) }));
jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(30_000);

const NOW = new Date(2026, 10, 24, 12);

function saved(body: WorkoutPlanInput, extra: Partial<WorkoutPlan> = {}): WorkoutPlan {
    return {
        id: 'own-1',
        status: 'DRAFT',
        version: 1,
        name: body.name,
        startsOn: body.startsOn,
        validityWeeks: body.validityWeeks,
        divisions: body.divisions.map((division, position) => ({
            letter: String.fromCharCode(65 + position),
            name: division.name ?? null,
            items: (division.items ?? []).map((item) => ({
                ...item,
                repsMax: item.repsMax ?? item.repsMin,
                alternatives: [],
            })),
        })),
        alerts: [],
        ...extra,
    };
}

function api(overrides: Partial<OwnPlanApi> = {}): OwnPlanApi {
    return {
        list: jest.fn(async () => []),
        create: jest.fn(async (body: WorkoutPlanInput) => saved(body)),
        update: jest.fn(async (_id: string, body: WorkoutPlanInput) =>
            saved(body, { status: 'PUBLISHED', version: 3 }),
        ),
        publish: jest.fn(async () => ({ id: 'own-1', status: 'PUBLISHED' as const, version: 1 })),
        searchExercises: jest.fn(async () => [{ id: 'e-squat', name: 'Agachamento livre' }]),
        exerciseName: jest.fn(async () => 'Agachamento livre'),
        ...overrides,
    };
}

async function addSquat(user: ReturnType<typeof userEvent.setup>, letter = 'A') {
    await user.type(screen.getByLabelText(`Buscar exercício para o treino ${letter}`), 'agach');
    await user.press(screen.getByRole('button', { name: `Buscar para o treino ${letter}` }));
    await user.press(
        await screen.findByRole('button', { name: `Adicionar Agachamento livre ao treino ${letter}` }),
    );
}

describe('minha ficha', () => {
    it('cria a ficha com exercício do catálogo e publica com confirmação', async () => {
        const user = userEvent.setup();
        const plans = api();
        await render(<OwnPlanScreen api={plans} now={NOW} onDone={jest.fn()} />);

        await user.type(await screen.findByLabelText('Nome da ficha'), 'Minha ficha');
        expect(screen.getByLabelText('Início (dd/mm/aaaa)')).toHaveDisplayValue('24/11/2026');
        await user.type(screen.getByLabelText('Nome do treino A'), 'Pernas');
        await addSquat(user);
        const label = 'Agachamento livre (treino A)';
        await user.clear(screen.getByLabelText(`Séries de ${label}`));
        await user.type(screen.getByLabelText(`Séries de ${label}`), '4');
        await user.type(screen.getByLabelText(`Até (faixa, opcional) de ${label}`), '12');
        await user.type(screen.getByLabelText(`Carga (kg, opcional) de ${label}`), '40,5');
        await user.type(screen.getByLabelText(`Descanso (s, opcional) de ${label}`), '90');

        await user.press(screen.getByRole('button', { name: 'Publicar' }));
        const confirm = screen.getByLabelText('Confirmar publicação');
        expect(plans.publish).not.toHaveBeenCalled();
        await user.press(within(confirm).getByRole('button', { name: 'Confirmar publicação' }));

        expect(plans.create).toHaveBeenCalledWith({
            baseVersion: null,
            name: 'Minha ficha',
            startsOn: '2026-11-24',
            validityWeeks: 8,
            divisions: [
                {
                    name: 'Pernas',
                    items: [
                        {
                            exerciseId: 'e-squat',
                            sets: 4,
                            repsMin: 10,
                            repsMax: 12,
                            loadKg: 40.5,
                            restSeconds: 90,
                            note: null,
                            technique: null,
                            alternatives: [],
                        },
                    ],
                },
            ],
        });
        await waitFor(() => expect(plans.publish).toHaveBeenCalledWith('own-1'));
        expect(
            await screen.findByText('Ficha publicada: ela já aparece no Treino de hoje.'),
        ).toBeOnTheScreen();
    });

    it('divisão sem exercício impede publicar', async () => {
        const user = userEvent.setup();
        const plans = api();
        await render(<OwnPlanScreen api={plans} now={NOW} onDone={jest.fn()} />);

        await user.type(await screen.findByLabelText('Nome da ficha'), 'Minha ficha');
        await addSquat(user);
        await user.press(screen.getByRole('button', { name: 'Adicionar treino B' }));
        await user.press(screen.getByRole('button', { name: 'Publicar' }));

        expect(screen.getByText('Cada divisão precisa de ao menos um exercício.')).toBeOnTheScreen();
        expect(screen.queryByLabelText('Confirmar publicação')).toBeNull();
        expect(plans.create).not.toHaveBeenCalled();
    });

    it('erro de validação aparece no campo do item, local e da API', async () => {
        const user = userEvent.setup();
        const plans = api({
            create: jest.fn(async () => {
                throw new ApiProblemError(400, 'Um ou mais campos são inválidos.', {
                    'divisions[0].items[0].loadKg': 'A carga vai de 0 a 1000 kg, em passos de 0,5.',
                });
            }),
        });
        await render(<OwnPlanScreen api={plans} now={NOW} onDone={jest.fn()} />);

        await user.type(await screen.findByLabelText('Nome da ficha'), 'Minha ficha');
        await addSquat(user);
        const sets = screen.getByLabelText('Séries de Agachamento livre (treino A)');
        await user.clear(sets);
        await user.type(sets, '0');
        await user.press(screen.getByRole('button', { name: 'Salvar ficha' }));
        expect(screen.getByText('As séries vão de 1 a 20.')).toBeOnTheScreen();
        expect(plans.create).not.toHaveBeenCalled();

        await user.clear(sets);
        await user.type(sets, '3');
        await user.press(screen.getByRole('button', { name: 'Salvar ficha' }));
        expect(await screen.findByText('A carga vai de 0 a 1000 kg, em passos de 0,5.')).toBeOnTheScreen();
        expect(screen.getByLabelText('Séries de Agachamento livre (treino A)')).toHaveDisplayValue('3');
    });

    it('editar a ficha publicada avisa que gera nova versão e manda a versão base', async () => {
        const user = userEvent.setup();
        const published: WorkoutPlan = {
            id: 'own-1',
            status: 'PUBLISHED',
            version: 2,
            name: 'Minha ficha',
            startsOn: '2026-11-01',
            validityWeeks: 8,
            divisions: [
                {
                    letter: 'A',
                    name: null,
                    items: [{ exerciseId: 'e-squat', sets: 3, repsMin: 10, repsMax: 10 }],
                },
            ],
        };
        const plans = api({ list: jest.fn(async () => [published]) });
        await render(<OwnPlanScreen api={plans} now={NOW} onDone={jest.fn()} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Esta ficha está publicada: salvar gera a versão 3, e os próximos treinos usam a nova.',
        );
        expect(screen.getByText('Agachamento livre')).toBeOnTheScreen();
        expect(screen.getByLabelText('Início (dd/mm/aaaa)')).toHaveDisplayValue('01/11/2026');
        expect(screen.queryByRole('button', { name: 'Publicar' })).toBeNull();
        await user.press(screen.getByRole('button', { name: 'Salvar ficha' }));

        expect(plans.update).toHaveBeenCalledWith('own-1', expect.objectContaining({ baseVersion: 2 }));
        expect(await screen.findByText('Ficha salva: versão 3 publicada.')).toBeOnTheScreen();
    });

    it('do convite do treino de hoje à ficha publicada aparecendo nele', async () => {
        const user = userEvent.setup();
        const active: ActivePlan = {
            planId: 'own-1',
            name: 'Minha ficha',
            nextDivision: 0,
            nextDivisionLetter: 'A',
            divisions: [{ position: 0, letter: 'A', name: null, items: [] }],
        };
        const workouts: WorkoutsApi = {
            activePlans: jest
                .fn<Promise<ActivePlan[]>, []>()
                .mockResolvedValueOnce([])
                .mockResolvedValueOnce([active]),
            start: jest.fn(),
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
        const plans = api();
        await render(
            <ClientArea
                api={profile}
                workoutsApi={workouts}
                ownPlanApi={plans}
                linkSource={{
                    getInitialURL: async () => null,
                    addEventListener: () => ({ remove: () => undefined }),
                }}
            />,
        );

        await user.press(await screen.findByRole('button', { name: 'Treino de hoje' }));
        await user.press(await screen.findByRole('button', { name: 'Montar minha ficha' }));
        await user.type(await screen.findByLabelText('Nome da ficha'), 'Minha ficha');
        await addSquat(user);
        await user.press(screen.getByRole('button', { name: 'Publicar' }));
        await user.press(screen.getByRole('button', { name: 'Confirmar publicação' }));
        await screen.findByText('Ficha publicada: ela já aparece no Treino de hoje.');
        await user.press(screen.getByRole('button', { name: 'Voltar' }));
        await user.press(await screen.findByRole('button', { name: 'Treino de hoje' }));

        expect(await screen.findByText('Minha ficha')).toBeOnTheScreen();
        expect(screen.getByText('Próximo: Treino A')).toBeOnTheScreen();
    });
});
