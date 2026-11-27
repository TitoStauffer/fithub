import { act, render, screen, userEvent } from '@testing-library/react-native';

import { ApiProblemError, OfflineError, type ClientProfile, type HealthProfile } from '../../lib/api';
import { ClientArea } from './ClientArea';
import type { ProgressApi } from '../progress/progressApi';
import type { ProfileApi } from './profileApi';
import { ProfileFormScreen } from './ProfileFormScreen';

// A área usa a sessão só para montar a API real; aqui a API é injetada.
jest.mock('../auth/SessionProvider', () => ({ useSession: () => ({ apiFetch: jest.fn() }) }));
jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const PROFILE: ClientProfile = {
    name: 'Ana Cliente',
    birthDate: '1990-11-24',
    age: 35,
    gender: 'FEMALE',
    heightCm: 165,
    goal: 'HEALTH',
};

const TODAY = new Date(2026, 10, 23);

/** Nenhum link aberto pelo sistema. */
const NO_LINK = { getInitialURL: async () => null, addEventListener: () => ({ remove: () => undefined }) };

const TERMS_TEXT = 'Ao salvar, você autoriza o FitHub a guardar as condições de saúde.';

function fakeApi(overrides: Partial<ProfileApi> = {}): ProfileApi & { saved: unknown[] } {
    const saved: unknown[] = [];
    return {
        saved,
        fetchProfile: jest.fn(async () => null),
        saveProfile: jest.fn(async (body) => {
            saved.push(body);
            return { ...PROFILE, ...body, age: 35 };
        }),
        fetchHealth: jest.fn(async () => null),
        saveHealth: jest.fn(async (body) => {
            saved.push(body);
            return {
                conditions: body.conditions ?? [],
                notes: body.notes ?? null,
                updatedAt: '2026-11-23T12:00:00Z',
            };
        }),
        fetchHealthTerms: jest.fn(async () => ({
            termsVersion: 'v1',
            termsText: TERMS_TEXT,
            accepted: true,
        })),
        ...overrides,
    };
}

async function fillProfile(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Nome'), 'Ana Cliente');
    await user.type(screen.getByLabelText('Data de nascimento'), '24/11/1990');
    await user.press(screen.getByRole('checkbox', { name: 'Feminino' }));
    await user.type(screen.getByLabelText('Altura (cm)'), '165');
    await user.press(screen.getByRole('checkbox', { name: 'Saúde' }));
}

function renderForm(api: ProfileApi, onSaved = jest.fn()): Promise<unknown> {
    return render(<ProfileFormScreen api={api} today={TODAY} onSaved={onSaved} />);
}

describe('ClientArea', () => {
    it('cliente sem perfil vai para o cadastro; com perfil, para a tela inicial com a idade', async () => {
        await render(<ClientArea api={fakeApi()} linkSource={NO_LINK} />);
        expect(await screen.findByText('Seu perfil')).toBeOnTheScreen();

        await render(
            <ClientArea api={fakeApi({ fetchProfile: jest.fn(async () => PROFILE) })} linkSource={NO_LINK} />,
        );
        expect(await screen.findByText('Olá, Ana Cliente')).toBeOnTheScreen();
        expect(screen.getByText('35 anos')).toBeOnTheScreen();
    });

    it('cadastra o perfil e vai para a tela inicial', async () => {
        const user = userEvent.setup();
        const api = fakeApi();
        await render(<ClientArea api={api} linkSource={NO_LINK} />);
        await screen.findByText('Seu perfil');

        await fillProfile(user);
        await user.press(screen.getByRole('button', { name: 'Salvar perfil' }));

        expect(await screen.findByText('Olá, Ana Cliente')).toBeOnTheScreen();
        expect(api.saved[0]).toEqual({
            name: 'Ana Cliente',
            birthDate: '1990-11-24',
            gender: 'FEMALE',
            heightCm: 165,
            goal: 'HEALTH',
        });
    });

    it('edita o perfil começando pelo que já foi cadastrado', async () => {
        const user = userEvent.setup();
        await render(
            <ClientArea api={fakeApi({ fetchProfile: jest.fn(async () => PROFILE) })} linkSource={NO_LINK} />,
        );

        await user.press(await screen.findByRole('button', { name: 'Editar perfil' }));

        expect(screen.getByLabelText('Nome')).toHaveDisplayValue('Ana Cliente');
        expect(screen.getByLabelText('Data de nascimento')).toHaveDisplayValue('24/11/1990');
        expect(screen.getByRole('checkbox', { name: 'Feminino' })).toBeChecked();
    });

    it('sem conexão ao abrir, avisa e deixa tentar de novo', async () => {
        const user = userEvent.setup();
        const fetchProfile = jest
            .fn<Promise<ClientProfile | null>, []>()
            .mockRejectedValueOnce(new OfflineError())
            .mockResolvedValueOnce(PROFILE);
        await render(<ClientArea api={fakeApi({ fetchProfile })} linkSource={NO_LINK} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(/Sem conexão com o FitHub/);
        await user.press(screen.getByRole('button', { name: 'Tentar de novo' }));

        expect(await screen.findByText('Olá, Ana Cliente')).toBeOnTheScreen();
    });
});

describe('ProfileFormScreen', () => {
    it('valida no aparelho com as regras da API antes de enviar', async () => {
        const user = userEvent.setup();
        const api = fakeApi();
        await renderForm(api);

        await user.type(screen.getByLabelText('Data de nascimento'), '24/11/2010');
        await user.type(screen.getByLabelText('Altura (cm)'), '300');
        await user.press(screen.getByRole('button', { name: 'Salvar perfil' }));

        expect(screen.getByText('Informe seu nome (até 200 caracteres).')).toBeOnTheScreen();
        expect(screen.getByText('No momento o FitHub é só para maiores de 18 anos.')).toBeOnTheScreen();
        expect(screen.getByText('Altura em centímetros, de 100 a 250.')).toBeOnTheScreen();
        expect(screen.getByText('Informe o gênero (ou prefira não informar).')).toBeOnTheScreen();
        expect(api.saveProfile).not.toHaveBeenCalled();
    });

    it('erro da API aparece no campo sem perder o que foi digitado', async () => {
        const user = userEvent.setup();
        const api = fakeApi({
            saveProfile: jest.fn(async () => {
                throw new ApiProblemError(400, 'Um ou mais campos são inválidos.', {
                    heightCm: 'Altura recusada.',
                });
            }),
        });
        await renderForm(api);

        await fillProfile(user);
        await user.press(screen.getByRole('button', { name: 'Salvar perfil' }));

        expect(await screen.findByText('Altura recusada.')).toBeOnTheScreen();
        // No campo, não no aviso geral: a pessoa vê o que corrigir onde digitou.
        expect(screen.queryByRole('alert')).not.toBeOnTheScreen();
        expect(screen.getByLabelText('Nome')).toHaveDisplayValue('Ana Cliente');
        expect(screen.getByLabelText('Altura (cm)')).toHaveDisplayValue('165');
    });

    it('sem conexão ao salvar, avisa e mantém o que foi digitado', async () => {
        const user = userEvent.setup();
        await renderForm(fakeApi({ saveProfile: jest.fn(async () => Promise.reject(new OfflineError())) }));

        await fillProfile(user);
        await user.press(screen.getByRole('button', { name: 'Salvar perfil' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(/Sem conexão com o FitHub/);
        expect(screen.getByLabelText('Data de nascimento')).toHaveDisplayValue('24/11/1990');
    });

    it('não envia duas vezes enquanto o primeiro envio não termina', async () => {
        const user = userEvent.setup();
        let release!: (profile: ClientProfile) => void;
        const saveProfile = jest.fn(() => new Promise<ClientProfile>((resolve) => (release = resolve)));
        await renderForm(fakeApi({ saveProfile }));

        await fillProfile(user);
        await user.press(screen.getByRole('button', { name: 'Salvar perfil' }));
        await user.press(screen.getByRole('button', { name: 'Salvando…' }));

        expect(saveProfile).toHaveBeenCalledTimes(1);
        await act(async () => release(PROFILE));
    });
});

describe('HealthFormScreen', () => {
    it('na primeira vez mostra o termo com a caixa desmarcada e só envia com ela marcada (DEV-0179)', async () => {
        const user = userEvent.setup();
        const api = fakeApi({
            fetchProfile: jest.fn(async () => PROFILE),
            fetchHealthTerms: jest.fn(async () => ({
                termsVersion: 'v1',
                termsText: TERMS_TEXT,
                accepted: false,
            })),
        });
        await render(<ClientArea api={api} linkSource={NO_LINK} />);
        await user.press(await screen.findByRole('button', { name: 'Perfil de saúde' }));

        expect(await screen.findByText(TERMS_TEXT)).toBeOnTheScreen();
        const consent = screen.getByRole('checkbox', { name: 'Li e aceito o termo do perfil de saúde' });
        expect(consent).not.toBeChecked();

        await user.press(screen.getByRole('button', { name: 'Salvar saúde' }));
        expect(screen.getByText('Para salvar, aceite o termo do perfil de saúde.')).toBeOnTheScreen();
        expect(api.saveHealth).not.toHaveBeenCalled();

        await user.press(consent);
        await user.press(screen.getByRole('button', { name: 'Salvar saúde' }));

        expect(await screen.findByText('Perfil de saúde salvo.')).toBeOnTheScreen();
        expect(api.saved[0]).toEqual({ conditions: [], consent: true, termsVersion: 'v1' });
        expect(screen.queryByText(TERMS_TEXT)).not.toBeOnTheScreen();
    });

    it('com o termo trocado enquanto a tela estava aberta, mostra o erro e volta a pedir o aceite', async () => {
        const user = userEvent.setup();
        const fetchHealthTerms = jest
            .fn()
            .mockResolvedValueOnce({ termsVersion: 'v1', termsText: TERMS_TEXT, accepted: true })
            .mockResolvedValue({
                termsVersion: 'v2',
                termsText: 'Termo novo do perfil de saúde.',
                accepted: false,
            });
        const api = fakeApi({
            fetchProfile: jest.fn(async () => PROFILE),
            fetchHealthTerms,
            saveHealth: jest.fn(async () => {
                throw new ApiProblemError(400, 'Um ou mais campos são inválidos.', {
                    consent: 'Para salvar o perfil de saúde, aceite o termo do perfil de saúde.',
                });
            }),
        });
        await render(<ClientArea api={api} linkSource={NO_LINK} />);
        await user.press(await screen.findByRole('button', { name: 'Perfil de saúde' }));

        await user.press(await screen.findByRole('button', { name: 'Salvar saúde' }));

        expect(
            await screen.findByText('Para salvar o perfil de saúde, aceite o termo do perfil de saúde.'),
        ).toBeOnTheScreen();
        expect(await screen.findByText('Termo novo do perfil de saúde.')).toBeOnTheScreen();
        expect(
            screen.getByRole('checkbox', { name: 'Li e aceito o termo do perfil de saúde' }),
        ).not.toBeChecked();
    });

    it('com o termo já aceito não mostra o termo nem manda o aceite', async () => {
        const user = userEvent.setup();
        const api = fakeApi({ fetchProfile: jest.fn(async () => PROFILE) });
        await render(<ClientArea api={api} linkSource={NO_LINK} />);
        await user.press(await screen.findByRole('button', { name: 'Perfil de saúde' }));

        await user.press(await screen.findByRole('button', { name: 'Salvar saúde' }));

        expect(await screen.findByText('Perfil de saúde salvo.')).toBeOnTheScreen();
        expect(screen.queryByText(TERMS_TEXT)).not.toBeOnTheScreen();
        expect(api.saved[0]).toEqual({ conditions: [] });
    });

    it('mostra a saúde cadastrada e salva as condições e as observações', async () => {
        const user = userEvent.setup();
        const health: HealthProfile = {
            conditions: ['DIABETES'],
            notes: 'joelho',
            updatedAt: '2026-11-22T10:00:00Z',
        };
        const api = fakeApi({
            fetchProfile: jest.fn(async () => PROFILE),
            fetchHealth: jest.fn(async () => health),
        });
        await render(<ClientArea api={api} linkSource={NO_LINK} />);

        await user.press(await screen.findByRole('button', { name: 'Perfil de saúde' }));
        expect(await screen.findByRole('checkbox', { name: 'Diabetes' })).toBeChecked();
        expect(screen.getByLabelText('Observações')).toHaveDisplayValue('joelho');

        await user.press(screen.getByRole('checkbox', { name: 'Hipertensão' }));
        await user.press(screen.getByRole('button', { name: 'Salvar saúde' }));

        expect(await screen.findByText('Perfil de saúde salvo.')).toBeOnTheScreen();
        expect(api.saved[0]).toEqual({ conditions: ['DIABETES', 'HYPERTENSION'], notes: 'joelho' });
    });

    it('observações acima de 1000 caracteres são recusadas antes de enviar', async () => {
        const user = userEvent.setup();
        const api = fakeApi({ fetchProfile: jest.fn(async () => PROFILE) });
        await render(<ClientArea api={api} linkSource={NO_LINK} />);
        await user.press(await screen.findByRole('button', { name: 'Perfil de saúde' }));

        await act(async () => {
            screen.getByLabelText('Observações').props.onChangeText('a'.repeat(1001));
        });
        await user.press(screen.getByRole('button', { name: 'Salvar saúde' }));

        expect(screen.getByText('O texto livre pode ter até 1000 caracteres.')).toBeOnTheScreen();
        expect(api.saveHealth).not.toHaveBeenCalled();
    });

    it('abre a minha evolução e vai dela para os recordes', async () => {
        const user = userEvent.setup();
        const progressApi: ProgressApi = {
            training: jest.fn(async () => ({
                adherence: { done: 1, expected: 2, percent: 50, weeks: [] },
                exercises: [],
            })),
            diet: jest.fn(async () => ({})),
            body: jest.fn(async () => ({})),
        };
        await render(
            <ClientArea
                api={fakeApi({ fetchProfile: jest.fn(async () => PROFILE) })}
                linkSource={NO_LINK}
                progressApi={progressApi}
                historyApi={{
                    history: jest.fn(),
                    records: jest.fn(async () => []),
                    addRecord: jest.fn(),
                    searchExercises: jest.fn(),
                }}
            />,
        );

        await user.press(await screen.findByRole('button', { name: 'Minha evolução' }));
        expect(await screen.findByText('Aderência: 1 de 2 treinos esperados (50%)')).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Ver recordes' }));
        expect(await screen.findByRole('header', { name: 'Recordes' })).toBeOnTheScreen();
    });
});
