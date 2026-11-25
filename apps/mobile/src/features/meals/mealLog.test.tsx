import { render, screen, userEvent, waitFor } from '@testing-library/react-native';

import {
    ApiProblemError,
    OfflineError,
    type MealLog,
    type MealLogInput,
    type MealPlanToday,
} from '../../lib/api';
import { MealLogScreen } from './MealLogScreen';
import type { MealPhotoSource } from './mealPhotoSource';
import type { MealsApi } from './mealsApi';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'id-do-aparelho' }));

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const rice = {
    foodId: 'f-rice',
    foodName: 'Arroz, tipo 1, cozido',
    baseUnit: 'G' as const,
    amount: 100,
    alternatives: [
        { foodId: 'f-pasta', foodName: 'Macarrão, trigo, cozido', baseUnit: 'G' as const, amount: 80 },
        { foodId: 'f-potato', foodName: 'Batata, inglesa, cozida', baseUnit: 'G' as const, amount: 150 },
    ],
};
const beans = {
    foodId: 'f-beans',
    foodName: 'Feijão, carioca, cozido',
    baseUnit: 'G' as const,
    amount: 50,
    alternatives: [],
};

function today(logs: MealLog[] = []): MealPlanToday {
    return {
        plan: {
            id: 'mp-1',
            version: 1,
            name: 'Plano',
            meals: [
                { position: 0, name: 'Café', items: [beans] },
                { position: 1, name: 'Almoço', items: [rice, beans] },
            ],
        },
        logs,
    };
}

function api(overrides: Partial<MealsApi> = {}): MealsApi {
    return {
        today: jest.fn(async () => today()),
        log: jest.fn(async (logId: string, body: MealLogInput) => ({ id: logId, ...body }) as MealLog),
        requestPhotoUpload: jest.fn(async () => ({ uploadUrl: 'https://s3/upload', uploadId: 'u-1' })),
        uploadPhoto: jest.fn(async () => undefined),
        confirmPhoto: jest.fn(
            async (logId: string) => ({ id: logId, photoUrl: 'https://s3/ler' }) as MealLog,
        ),
        ...overrides,
    };
}

function photos(overrides: Partial<MealPhotoSource> = {}): MealPhotoSource {
    return {
        take: jest.fn(async () => ({
            uri: 'file:///cache/prato.jpg',
            contentType: 'image/jpeg',
            fromCamera: true,
        })),
        pick: jest.fn(async () => ({
            uri: 'content://galeria/prato.png',
            contentType: 'image/png',
            fromCamera: false,
        })),
        discard: jest.fn(async () => undefined),
        ...overrides,
    };
}

describe('registro da refeição', () => {
    it('registra a refeição parcial com a alternativa usada, o desvio e a foto da câmera', async () => {
        const user = userEvent.setup();
        const meals = api();
        const camera = photos();
        const onDone = jest.fn();
        await render(
            <MealLogScreen api={meals} photos={camera} today={today()} mealPosition={1} onDone={onDone} />,
        );

        await user.press(screen.getByRole('radio', { name: 'Parcial' }));
        await user.press(screen.getByRole('radio', { name: 'Batata, inglesa, cozida (150 g)' }));
        await user.type(screen.getByLabelText('Desvio (opcional)'), 'Comi só metade');
        await user.press(screen.getByRole('button', { name: 'Tirar foto' }));
        expect(
            await screen.findByText('Foto do prato escolhida: vai junto com o registro.'),
        ).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));

        await waitFor(() => expect(onDone).toHaveBeenCalledWith(true));
        expect(meals.log).toHaveBeenCalledWith('id-do-aparelho', {
            planId: 'mp-1',
            mealPosition: 1,
            status: 'PARTIAL',
            substitutions: [{ itemPosition: 0, alternative: 1 }],
            deviation: 'Comi só metade',
        });
        expect(meals.requestPhotoUpload).toHaveBeenCalledWith('id-do-aparelho', 'image/jpeg');
        expect(meals.uploadPhoto).toHaveBeenCalledWith(
            'https://s3/upload',
            'file:///cache/prato.jpg',
            'image/jpeg',
        );
        expect(meals.confirmPhoto).toHaveBeenCalledWith('id-do-aparelho', 'u-1');
        // A foto tirada para o envio não fica no aparelho.
        expect(camera.discard).toHaveBeenCalledWith('file:///cache/prato.jpg');
    });

    it('a refeição não feita vai sem alternativa, e a foto da galeria não é apagada do aparelho', async () => {
        const user = userEvent.setup();
        const meals = api();
        const gallery = photos();
        await render(
            <MealLogScreen
                api={meals}
                photos={gallery}
                today={today()}
                mealPosition={1}
                onDone={jest.fn()}
            />,
        );

        await user.press(screen.getByRole('radio', { name: 'Feita' }));
        await user.press(screen.getByRole('radio', { name: 'Macarrão, trigo, cozido (80 g)' }));
        await user.press(screen.getByRole('radio', { name: 'Não feita' }));
        expect(screen.queryByText('O que você comeu no lugar de Arroz, tipo 1, cozido?')).toBeNull();
        await user.press(screen.getByRole('button', { name: 'Escolher da galeria' }));
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));

        await waitFor(() => expect(meals.confirmPhoto).toHaveBeenCalled());
        expect(meals.log).toHaveBeenCalledWith(
            'id-do-aparelho',
            expect.objectContaining({ status: 'SKIPPED', substitutions: [] }),
        );
        expect(meals.requestPhotoUpload).toHaveBeenCalledWith('id-do-aparelho', 'image/png');
        expect(gallery.discard).not.toHaveBeenCalled();
    });

    it('corrige o registro de hoje com o mesmo id, começando pelo que já foi registrado', async () => {
        const user = userEvent.setup();
        const meals = api();
        const existing: MealLog = {
            id: 'l-antigo',
            mealPosition: 1,
            status: 'DONE',
            substitutions: [{ itemPosition: 0, alternative: 0 }],
            deviation: 'Sem sal',
            photoUrl: 'https://s3/ler',
        };
        await render(
            <MealLogScreen
                api={meals}
                photos={photos()}
                today={today([existing])}
                mealPosition={1}
                newId={() => 'id-novo'}
                onDone={jest.fn()}
            />,
        );

        expect(screen.getByText('Corrigir registro: Almoço')).toBeOnTheScreen();
        expect(screen.getByRole('radio', { name: 'Feita' })).toBeChecked();
        expect(screen.getByRole('radio', { name: 'Macarrão, trigo, cozido (80 g)' })).toBeChecked();
        expect(screen.getByLabelText('Desvio (opcional)')).toHaveDisplayValue('Sem sal');
        expect(screen.getByText('Foto do prato já enviada. Escolha outra para trocar.')).toBeOnTheScreen();
        await user.press(screen.getByRole('radio', { name: 'Arroz, tipo 1, cozido (100 g)' }));
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));

        await waitFor(() => expect(meals.log).toHaveBeenCalled());
        expect(meals.log).toHaveBeenCalledWith(
            'l-antigo',
            expect.objectContaining({ status: 'DONE', substitutions: [] }),
        );
        expect(meals.requestPhotoUpload).not.toHaveBeenCalled();
    });

    it('valida antes de enviar e mostra o 409 como aviso', async () => {
        const user = userEvent.setup();
        const meals = api({
            log: jest.fn(async () => {
                throw new ApiProblemError(
                    409,
                    'O registro só pode ser corrigido no dia em que foi feito.',
                    {},
                );
            }),
        });
        const onDone = jest.fn();
        await render(
            <MealLogScreen api={meals} photos={photos()} today={today()} mealPosition={0} onDone={onDone} />,
        );

        await user.type(screen.getByLabelText('Desvio (opcional)'), 'a'.repeat(501));
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));
        expect(screen.getByText('Escolha como foi a refeição.')).toBeOnTheScreen();
        expect(screen.getByText('O desvio pode ter até 500 caracteres.')).toBeOnTheScreen();
        expect(meals.log).not.toHaveBeenCalled();

        await user.clear(screen.getByLabelText('Desvio (opcional)'));
        await user.press(screen.getByRole('radio', { name: 'Feita' }));
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));
        expect(
            await screen.findByText(
                'Não deu para gravar: O registro só pode ser corrigido no dia em que foi feito.',
            ),
        ).toBeOnTheScreen();
        expect(onDone).not.toHaveBeenCalled();
    });

    it('sem conexão, avisa e reenvia com o mesmo id', async () => {
        const user = userEvent.setup();
        const log = jest
            .fn<Promise<MealLog>, [string, MealLogInput]>()
            .mockRejectedValueOnce(new OfflineError())
            .mockImplementationOnce(async (logId, body) => ({ id: logId, ...body }) as MealLog);
        const onDone = jest.fn();
        let ids = 0;
        await render(
            <MealLogScreen
                api={api({ log })}
                photos={photos()}
                today={today()}
                mealPosition={0}
                newId={() => `id-${++ids}`}
                onDone={onDone}
            />,
        );

        await user.press(screen.getByRole('radio', { name: 'Feita' }));
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));
        expect(await screen.findByText(/Sem conexão com o FitHub/)).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));

        await waitFor(() => expect(onDone).toHaveBeenCalledWith(true));
        expect(log.mock.calls.map(([logId]) => logId)).toEqual(['id-1', 'id-1']);
    });

    it('o erro de campo da API aparece no formulário', async () => {
        const user = userEvent.setup();
        const meals = api({
            log: jest.fn(async () => {
                throw new ApiProblemError(400, 'Um ou mais campos são inválidos.', {
                    deviation: 'O desvio pode ter até 500 caracteres.',
                });
            }),
        });
        await render(
            <MealLogScreen
                api={meals}
                photos={photos()}
                today={today()}
                mealPosition={0}
                onDone={jest.fn()}
            />,
        );

        await user.press(screen.getByRole('radio', { name: 'Parcial' }));
        await user.press(screen.getByRole('button', { name: 'Salvar registro' }));
        expect(await screen.findByText('O desvio pode ter até 500 caracteres.')).toBeOnTheScreen();
    });
});
