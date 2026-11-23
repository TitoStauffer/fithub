import { render, screen, userEvent } from '@testing-library/react-native';

import { OfflineError, type Assessment, type AssessmentRequest } from '../../lib/api';
import type { AssessmentApi, PhotoSource } from './assessmentApi';
import { OnlineAssessmentScreen } from './OnlineAssessmentScreen';

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const NOW = new Date(2026, 10, 23, 12);

function api(overrides: Partial<AssessmentApi> = {}): AssessmentApi {
    return {
        record: jest.fn(async (body: AssessmentRequest): Promise<Assessment> => ({
            id: 'a1',
            type: 'ONLINE',
            measuredOn: body.measuredOn,
            weightKg: body.weightKg,
            bodyFat: body.circumferences?.waist ? [{ origin: 'NAVY', percent: 26.1 }] : [],
        })),
        requestUpload: jest.fn(async () => ({ uploadId: 'up-1', uploadUrl: 'https://fotos.example/put' })),
        upload: jest.fn(async () => undefined),
        confirm: jest.fn(async () => ({ pose: 'FRONT' as const })),
        ...overrides,
    };
}

function camera(): PhotoSource & { discard: jest.Mock } {
    return {
        take: jest.fn(async () => ({ uri: 'file:///cache/foto.jpg', contentType: 'image/jpeg' })),
        discard: jest.fn(async () => undefined),
    };
}

type User = ReturnType<typeof userEvent.setup>;

async function startWith(user: User, weight: string, sex?: string) {
    await user.type(screen.getByLabelText('Peso (kg)'), weight);
    if (sex) await user.press(screen.getByLabelText(sex));
    await user.press(screen.getByText('Começar as medidas'));
}

async function measure(user: User, label: string, value: string) {
    await user.type(screen.getByLabelText(`${label} (cm)`), value);
    await user.press(screen.getByText('Próxima'));
}

async function skipRest(user: User, from: number) {
    for (let index = from; index <= 9; index++) await user.press(screen.getByText('Pular esta medida'));
}

describe('avaliação online guiada', () => {
    it('mostra como medir cada circunferência, revisa e envia; o % aparece marcado como online', async () => {
        const user = userEvent.setup();
        const assessments = api();
        await render(
            <OnlineAssessmentScreen api={assessments} photos={camera()} onDone={jest.fn()} now={NOW} />,
        );

        await startWith(user, '64,5', 'Feminino');
        expect(screen.getByText('Medida 1 de 9')).toBeOnTheScreen();
        expect(screen.getByText(/logo abaixo do pomo de adão/)).toBeOnTheScreen();
        await measure(user, 'Pescoço', '33');
        expect(screen.getByText(/parte mais fina entre as costelas e o umbigo/)).toBeOnTheScreen();
        await measure(user, 'Cintura', '70,5');
        await measure(user, 'Quadril', '98');
        await skipRest(user, 4);

        expect(screen.getByText('Revise antes de enviar')).toBeOnTheScreen();
        expect(screen.queryByText(/não será calculado/)).toBeNull();
        await user.press(screen.getByText('Enviar avaliação'));

        expect(
            await screen.findByText('% de gordura estimado (online, fórmula da Marinha): 26.1%'),
        ).toBeOnTheScreen();
        expect(assessments.record).toHaveBeenCalledWith({
            measuredOn: '2026-11-23',
            weightKg: 64.5,
            sexForFormulas: 'FEMALE',
            circumferences: { neck: 33, waist: 70.5, hip: 98 },
        });
    });

    it('não deixa seguir sem a medida digitada ou fora da faixa', async () => {
        const user = userEvent.setup();
        await render(<OnlineAssessmentScreen api={api()} photos={camera()} onDone={jest.fn()} now={NOW} />);

        await user.press(screen.getByText('Começar as medidas'));
        expect(screen.getByText('O peso vai de 20 a 400 kg, com até 1 casa decimal.')).toBeOnTheScreen();
        await startWith(user, '70');
        await user.press(screen.getByText('Próxima'));
        expect(screen.getByText('Digite a medida ou toque em "Pular esta medida".')).toBeOnTheScreen();
        await measure(user, 'Pescoço', '5');
        expect(screen.getByText('De 10 a 300 cm, com até 1 casa decimal.')).toBeOnTheScreen();
    });

    it('medida pulada não vai para a API e avisa que a estimativa não será calculada', async () => {
        const user = userEvent.setup();
        const assessments = api();
        await render(
            <OnlineAssessmentScreen api={assessments} photos={camera()} onDone={jest.fn()} now={NOW} />,
        );

        await startWith(user, '80', 'Masculino');
        await measure(user, 'Pescoço', '38');
        await user.press(screen.getByText('Pular esta medida'));
        await skipRest(user, 3);

        expect(screen.getByText('Cintura: pulada')).toBeOnTheScreen();
        expect(
            screen.getByText('Sem cintura, o % de gordura estimado não será calculado.'),
        ).toBeOnTheScreen();
        await user.press(screen.getByText('Enviar avaliação'));

        expect(await screen.findByText(/% de gordura estimado não calculado/)).toBeOnTheScreen();
        expect(assessments.record).toHaveBeenCalledWith({
            measuredOn: '2026-11-23',
            weightKg: 80,
            sexForFormulas: 'MALE',
            circumferences: { neck: 38 },
        });
    });

    it('sem conexão, avisa e mantém o que foi preenchido para enviar de novo', async () => {
        const user = userEvent.setup();
        const record = jest
            .fn<Promise<Assessment>, [AssessmentRequest]>()
            .mockRejectedValueOnce(new OfflineError())
            .mockResolvedValueOnce({ id: 'a1', bodyFat: [] });
        await render(
            <OnlineAssessmentScreen api={api({ record })} photos={camera()} onDone={jest.fn()} now={NOW} />,
        );

        await startWith(user, '70');
        await measure(user, 'Pescoço', '36');
        await skipRest(user, 2);
        await user.press(screen.getByText('Enviar avaliação'));

        expect(await screen.findByRole('alert')).toHaveTextContent(/Sem conexão com o FitHub/);
        expect(screen.getByText('Pescoço: 36 cm')).toBeOnTheScreen();
        // Da revisão até a primeira medida: nove passos para trás.
        for (let index = 0; index < 9; index++) await user.press(screen.getByText('Voltar'));
        expect(screen.getByLabelText('Pescoço (cm)')).toHaveDisplayValue('36');

        await skipRest(user, 1);
        await user.press(screen.getByText('Enviar avaliação'));
        expect(await screen.findByText('Avaliação enviada')).toBeOnTheScreen();
        expect(record).toHaveBeenCalledTimes(2);
    });
});

describe('fotos da avaliação', () => {
    async function savedAssessment(user: User, assessments: AssessmentApi, photos: PhotoSource) {
        await render(
            <OnlineAssessmentScreen api={assessments} photos={photos} onDone={jest.fn()} now={NOW} />,
        );
        await startWith(user, '70');
        await skipRest(user, 1);
        await user.press(screen.getByText('Enviar avaliação'));
        await screen.findByText('Avaliação enviada');
    }

    it('envia a foto da pose pela URL pré-assinada, confirma e apaga o arquivo do aparelho', async () => {
        const user = userEvent.setup();
        const assessments = api();
        const photos = camera();
        await savedAssessment(user, assessments, photos);

        await user.press(screen.getByText('Tirar foto de frente'));

        expect(await screen.findByText('Foto de frente enviada')).toBeOnTheScreen();
        expect(assessments.requestUpload).toHaveBeenCalledWith('a1', 'FRONT', 'image/jpeg');
        expect(assessments.upload).toHaveBeenCalledWith(
            'https://fotos.example/put',
            'file:///cache/foto.jpg',
            'image/jpeg',
        );
        expect(assessments.confirm).toHaveBeenCalledWith('a1', 'FRONT', 'up-1');
        expect(photos.discard).toHaveBeenCalledWith('file:///cache/foto.jpg');
    });

    it('sem conexão no envio, avisa e a foto também sai do aparelho', async () => {
        const user = userEvent.setup();
        const assessments = api({ upload: jest.fn(async () => Promise.reject(new OfflineError())) });
        const photos = camera();
        await savedAssessment(user, assessments, photos);

        await user.press(screen.getByText('Tirar foto de lado'));

        expect(await screen.findByRole('alert')).toHaveTextContent(/A foto não ficou guardada no aparelho/);
        expect(assessments.confirm).not.toHaveBeenCalled();
        expect(photos.discard).toHaveBeenCalledWith('file:///cache/foto.jpg');
        expect(screen.queryByText('Foto de lado enviada')).toBeNull();
    });
});
