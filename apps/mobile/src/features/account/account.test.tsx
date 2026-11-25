import { render, screen, userEvent } from '@testing-library/react-native';

import { ApiProblemError, type ClientProfile } from '../../lib/api';
import { ClientArea, type LinkSource } from '../profile/ClientArea';
import type { ProfileApi } from '../profile/profileApi';
import { isConfirmed, type AccountApi } from './accountApi';
import { DeleteAccountScreen } from './DeleteAccountScreen';

// A sessão do mock registra o signOut: é ele que encerra a sessão depois da exclusão, quando a tela não recebe outro.
const mockSignOut = jest.fn(async () => undefined);
jest.mock('../auth/SessionProvider', () => ({
    useSession: () => ({ apiFetch: jest.fn(), session: { signOut: mockSignOut } }),
}));
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

const profileApi: ProfileApi = {
    fetchProfile: jest.fn(async () => PROFILE),
    saveProfile: jest.fn(),
    fetchHealth: jest.fn(async () => null),
    saveHealth: jest.fn(),
};

const noLinks: LinkSource = {
    getInitialURL: async () => null,
    addEventListener: () => ({ remove: () => undefined }),
};

const CONFIRM_LABEL = 'Para confirmar, digite EXCLUIR';
const DELETE = 'Excluir minha conta de vez';

function account(deleteAccount: AccountApi['deleteAccount'] = jest.fn(async () => undefined)): AccountApi {
    return { deleteAccount };
}

function deleteButton() {
    return screen.getByRole('button', { name: DELETE });
}

describe('confirmação da exclusão', () => {
    it('vale com EXCLUIR, sem diferenciar maiúsculas nem espaços nas pontas', () => {
        expect(isConfirmed('EXCLUIR')).toBe(true);
        expect(isConfirmed('  excluir ')).toBe(true);
        expect(isConfirmed('EXCLUI')).toBe(false);
        expect(isConfirmed('EXCLUIR CONTA')).toBe(false);
        expect(isConfirmed('')).toBe(false);
    });
});

describe('DeleteAccountScreen', () => {
    it('explica o que é apagado, que não tem volta e que as prescrições ficam sem identificar o cliente', async () => {
        await render(<DeleteAccountScreen api={account()} onDeleted={jest.fn()} onCancel={jest.fn()} />);

        expect(screen.getByText(/apaga de vez seu perfil, seu perfil de saúde/)).toBeOnTheScreen();
        expect(screen.getByText(/Não há como desfazer nem recuperar a conta/)).toBeOnTheScreen();
        expect(screen.getByText(/continuam com eles, sem nada que identifique você/)).toBeOnTheScreen();
        expect(screen.getByText(/também use o FitHub como profissional: aí o login fica/)).toBeOnTheScreen();
        expect(screen.getByText(/fica guardado por 6 meses/)).toBeOnTheScreen();
    });

    it('só exclui depois que o cliente digita a confirmação', async () => {
        const user = userEvent.setup();
        const api = account();
        const onDeleted = jest.fn();
        await render(<DeleteAccountScreen api={api} onDeleted={onDeleted} onCancel={jest.fn()} />);

        expect(deleteButton()).toBeDisabled();
        await user.press(deleteButton());
        expect(api.deleteAccount).not.toHaveBeenCalled();

        await user.type(screen.getByLabelText(CONFIRM_LABEL), 'EXCLUI');
        expect(deleteButton()).toBeDisabled();

        await user.type(screen.getByLabelText(CONFIRM_LABEL), 'R');
        expect(deleteButton()).toBeEnabled();
        await user.press(deleteButton());

        expect(api.deleteAccount).toHaveBeenCalledTimes(1);
        expect(onDeleted).toHaveBeenCalledTimes(1);
    });

    it('com erro, mostra a mensagem, não encerra a sessão e deixa tentar de novo', async () => {
        const user = userEvent.setup();
        const deleteAccount = jest
            .fn<Promise<void>, []>()
            .mockRejectedValueOnce(
                new ApiProblemError(500, 'O FitHub está fora do ar. Tente mais tarde.', {}),
            )
            .mockResolvedValueOnce(undefined);
        const onDeleted = jest.fn();
        await render(
            <DeleteAccountScreen api={account(deleteAccount)} onDeleted={onDeleted} onCancel={jest.fn()} />,
        );

        await user.type(screen.getByLabelText(CONFIRM_LABEL), 'EXCLUIR');
        await user.press(deleteButton());

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'O FitHub está fora do ar. Tente mais tarde.',
        );
        expect(onDeleted).not.toHaveBeenCalled();
        expect(deleteButton()).toBeEnabled();

        await user.press(deleteButton());
        expect(deleteAccount).toHaveBeenCalledTimes(2);
        expect(onDeleted).toHaveBeenCalledTimes(1);
    });

    it('cancelar volta sem excluir', async () => {
        const user = userEvent.setup();
        const api = account();
        const onCancel = jest.fn();
        await render(<DeleteAccountScreen api={api} onDeleted={jest.fn()} onCancel={onCancel} />);

        await user.press(screen.getByRole('button', { name: 'Cancelar' }));

        expect(onCancel).toHaveBeenCalledTimes(1);
        expect(api.deleteAccount).not.toHaveBeenCalled();
    });
});

describe('ClientArea', () => {
    it('leva da tela inicial à exclusão e, excluída a conta, encerra a sessão', async () => {
        const user = userEvent.setup();
        const api = account();
        const onAccountDeleted = jest.fn();
        await render(
            <ClientArea
                api={profileApi}
                linkSource={noLinks}
                accountApi={api}
                onAccountDeleted={onAccountDeleted}
            />,
        );

        await user.press(await screen.findByRole('button', { name: 'Excluir minha conta' }));
        await user.type(screen.getByLabelText(CONFIRM_LABEL), 'excluir');
        await user.press(deleteButton());

        expect(api.deleteAccount).toHaveBeenCalledTimes(1);
        expect(onAccountDeleted).toHaveBeenCalledTimes(1);
    });

    it('sem outro destino, encerra a sessão pelo signOut depois da exclusão', async () => {
        const user = userEvent.setup();
        mockSignOut.mockClear();
        const api = account();
        await render(<ClientArea api={profileApi} linkSource={noLinks} accountApi={api} />);

        await user.press(await screen.findByRole('button', { name: 'Excluir minha conta' }));
        await user.type(screen.getByLabelText(CONFIRM_LABEL), 'EXCLUIR');
        await user.press(deleteButton());

        expect(api.deleteAccount).toHaveBeenCalledTimes(1);
        expect(mockSignOut).toHaveBeenCalledTimes(1);
    });
});
