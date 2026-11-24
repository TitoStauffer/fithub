import { act, fireEvent, render, screen, userEvent, waitFor } from '@testing-library/react-native';

import { OfflineError, type Assessment, type Photo } from '../../lib/api';
import type { AssessmentApi } from './assessmentApi';
import { PhotoComparisonScreen } from './PhotoComparisonScreen';

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const HISTORY: Assessment[] = [
    { id: 'a2', type: 'ONLINE', measuredOn: '2026-11-20', weightKg: 63 },
    { id: 'a1', type: 'ONLINE', measuredOn: '2026-10-01', weightKg: 66 },
];

function photo(pose: Photo['pose'], url: string): Photo {
    return { pose, url, expiresAt: '2026-11-23T12:05:00Z' };
}

function api(photos: AssessmentApi['photos'], list: AssessmentApi['list'] = jest.fn(async () => HISTORY)) {
    return {
        record: jest.fn(),
        requestUpload: jest.fn(),
        upload: jest.fn(),
        confirm: jest.fn(),
        list,
        photos: jest.fn(photos),
    } satisfies AssessmentApi;
}

type User = ReturnType<typeof userEvent.setup>;

/** As duas listas mostram as mesmas datas: a primeira opção de cada data é da primeira lista. */
async function chooseBoth(user: User) {
    await screen.findByText('Comparar fotos');
    await user.press(screen.getAllByLabelText('01/10/2026')[0]!);
    await user.press(screen.getAllByLabelText('20/11/2026')[1]!);
}

describe('comparativo de fotos no app', () => {
    it('mostra as fotos da pose lado a lado, com as datas, pedindo a URL na hora', async () => {
        const user = userEvent.setup();
        const assessments = api(async (id) =>
            id === 'a1'
                ? [photo('FRONT', 'https://fotos.test/a1-front'), photo('BACK', 'https://fotos.test/a1-back')]
                : [photo('FRONT', 'https://fotos.test/a2-front')],
        );
        await render(<PhotoComparisonScreen api={assessments} onDone={jest.fn()} />);

        await chooseBoth(user);

        expect(await screen.findByLabelText('Foto de frente em 01/10/2026')).toHaveProp('source', {
            uri: 'https://fotos.test/a1-front',
        });
        expect(await screen.findByLabelText('Foto de frente em 20/11/2026')).toHaveProp('source', {
            uri: 'https://fotos.test/a2-front',
        });

        await user.press(screen.getByLabelText('Costas'));
        expect(await screen.findByLabelText('Foto de costas em 01/10/2026')).toBeOnTheScreen();
        expect(await screen.findByText('Sem foto de costas nesta avaliação.')).toBeOnTheScreen();
        // Trocar a pose pede as URLs de novo: elas valem só 5 minutos.
        expect(assessments.photos).toHaveBeenCalledTimes(4);
    });

    it('foto que não carrega (URL vencida) pede uma URL nova uma vez e depois avisa', async () => {
        const user = userEvent.setup();
        let issued = 0;
        const assessments = api(async (id) => {
            if (id === 'a2') return [];
            issued++;
            return [photo('FRONT', `https://fotos.test/a1-front-${issued}`)];
        });
        await render(<PhotoComparisonScreen api={assessments} onDone={jest.fn()} />);
        await chooseBoth(user);

        const first = await screen.findByLabelText('Foto de frente em 01/10/2026');
        // O pedido da URL nova termina dentro do act, sem atualizar a tela fora dele.
        await act(async () => {
            fireEvent(first, 'error');
        });
        expect(screen.getByLabelText('Foto de frente em 01/10/2026')).toHaveProp('source', {
            uri: 'https://fotos.test/a1-front-2',
        });

        fireEvent(screen.getByLabelText('Foto de frente em 01/10/2026'), 'error');
        expect(await screen.findByText('Não foi possível carregar a foto.')).toBeOnTheScreen();
        expect(issued).toBe(2);
        expect(screen.getByText('Sem foto de frente nesta avaliação.')).toBeOnTheScreen();
    });

    it('sem conexão, avisa em vez de mostrar a foto', async () => {
        const user = userEvent.setup();
        await render(
            <PhotoComparisonScreen
                api={api(async () => Promise.reject(new OfflineError()))}
                onDone={jest.fn()}
            />,
        );
        await chooseBoth(user);

        // As duas fotos falham: espera os dois avisos, para nada mudar a tela depois do fim do teste.
        await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(2));
        for (const alert of screen.getAllByRole('alert'))
            expect(alert).toHaveTextContent(/Sem conexão com o FitHub/);
    });

    it('com menos de duas avaliações, explica que não há o que comparar', async () => {
        await render(
            <PhotoComparisonScreen
                api={api(
                    async () => [],
                    jest.fn(async () => [HISTORY[0]!]),
                )}
                onDone={jest.fn()}
            />,
        );

        expect(
            await screen.findByText('Para comparar, você precisa de pelo menos duas avaliações.'),
        ).toBeOnTheScreen();
    });
});
