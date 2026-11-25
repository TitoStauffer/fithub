import { render, screen, userEvent } from '@testing-library/react-native';

import { OfflineError, type MealPlanToday } from '../../lib/api';
import { TodayMealsScreen } from './TodayMealsScreen';
import { amountText, differenceText, macrosText, type MealsApi } from './mealsApi';

// Telas inteiras no Jest: com os testes em paralelo, o primeiro render passa dos 5 s padrão.
jest.setTimeout(20_000);

const amounts = (energyKcal: number, proteinG: number, carbsG: number, fatG: number) => ({
    energyKcal,
    proteinG,
    carbsG,
    fatG,
});

const today: MealPlanToday = {
    plan: {
        id: 'mp-1',
        status: 'PUBLISHED',
        version: 2,
        name: 'Plano de dezembro',
        note: 'Beba 2 litros de água por dia.',
        meals: [
            {
                position: 0,
                name: 'Café da manhã',
                suggestedTime: '07:30',
                note: 'Sem açúcar.',
                items: [
                    {
                        foodId: 'f-rice',
                        foodName: 'Arroz, tipo 1, cozido',
                        baseUnit: 'G',
                        amount: 100,
                        nutrients: amounts(128, 2.5, 28.1, 0.2),
                        alternatives: [
                            {
                                foodId: 'f-pasta',
                                foodName: 'Macarrão, trigo, cozido',
                                baseUnit: 'G',
                                amount: 80,
                                difference: amounts(-46.4, 0.2, -12.2, 0.8),
                            },
                        ],
                        alerts: [],
                    },
                    {
                        foodId: 'f-beans',
                        foodName: 'Feijão, carioca, cozido',
                        baseUnit: 'G',
                        amount: 50,
                        measureId: 'm-ladle',
                        measureName: 'Concha',
                        measureCount: 2,
                        nutrients: amounts(38, 2.4, 6.8, 0.3),
                        alternatives: [],
                        alerts: [],
                    },
                ],
                total: amounts(166, 4.9, 34.9, 0.5),
            },
            {
                position: 1,
                name: 'Almoço',
                items: [
                    {
                        foodId: 'f-milk',
                        foodName: 'Leite, de vaca, integral',
                        baseUnit: 'ML',
                        amount: 200,
                        nutrients: amounts(122, 6.4, 9.2, 6.6),
                        alternatives: [],
                        alerts: [],
                    },
                ],
                total: amounts(122, 6.4, 9.2, 6.6),
            },
        ],
        day: amounts(288, 11.3, 44.1, 7.1),
    },
    logs: [{ id: 'l-1', mealPosition: 0, status: 'PARTIAL', version: 2, substitutions: [] }],
};

function api(overrides: Partial<MealsApi> = {}): MealsApi {
    return {
        today: jest.fn(async () => today),
        log: jest.fn(),
        requestPhotoUpload: jest.fn(),
        uploadPhoto: jest.fn(),
        confirmPhoto: jest.fn(),
        ...overrides,
    };
}

describe('refeições de hoje', () => {
    it('mostra as refeições com horário, alimentos, alternativas, macros e o registro de hoje', async () => {
        await render(<TodayMealsScreen api={api()} onDone={jest.fn()} />);

        expect(await screen.findByText('Plano de dezembro')).toBeOnTheScreen();
        expect(screen.getByText('Beba 2 litros de água por dia.')).toBeOnTheScreen();
        expect(screen.getByText('07:30 · Café da manhã')).toBeOnTheScreen();
        expect(screen.getByText('Sem açúcar.')).toBeOnTheScreen();
        expect(screen.getByText('Arroz, tipo 1, cozido — 100 g')).toBeOnTheScreen();
        expect(screen.getByText('Feijão, carioca, cozido — 2 Concha (50 g)')).toBeOnTheScreen();
        expect(screen.getByText('Leite, de vaca, integral — 200 ml')).toBeOnTheScreen();
        expect(
            screen.getByText(
                'Ou: Macarrão, trigo, cozido — 80 g (-46,4 kcal · P +0,2 g · C -12,2 g · G +0,8 g)',
            ),
        ).toBeOnTheScreen();
        expect(
            screen.getByText('Total da refeição: 166 kcal · P 4,9 g · C 34,9 g · G 0,5 g'),
        ).toBeOnTheScreen();
        expect(screen.getByText('Total do dia: 288 kcal · P 11,3 g · C 44,1 g · G 7,1 g')).toBeOnTheScreen();
        expect(screen.getByText('Registrada hoje: parcial')).toBeOnTheScreen();
        expect(screen.getByText('Ainda não registrada')).toBeOnTheScreen();
    });

    it('sem plano publicado, avisa', async () => {
        await render(<TodayMealsScreen api={api({ today: jest.fn(async () => null) })} onDone={jest.fn()} />);

        expect(await screen.findByText(/Você ainda não tem plano alimentar/)).toBeOnTheScreen();
    });

    it('sem conexão, avisa e deixa tentar de novo', async () => {
        const user = userEvent.setup();
        const meals = api({
            today: jest
                .fn<Promise<MealPlanToday | null>, []>()
                .mockRejectedValueOnce(new OfflineError())
                .mockResolvedValueOnce(today),
        });
        await render(<TodayMealsScreen api={meals} onDone={jest.fn()} />);

        expect(
            await screen.findByText('Sem conexão com o FitHub. Confira a internet e tente de novo.'),
        ).toBeOnTheScreen();
        await user.press(screen.getByRole('button', { name: 'Tentar de novo' }));
        expect(await screen.findByText('Plano de dezembro')).toBeOnTheScreen();
    });

    it('leva ao registro da refeição quando o app oferece', async () => {
        const user = userEvent.setup();
        const onLog = jest.fn();
        await render(<TodayMealsScreen api={api()} onLog={onLog} onDone={jest.fn()} />);

        await user.press(await screen.findByRole('button', { name: 'Registrar refeição' }));
        expect(onLog).toHaveBeenCalledWith(today, 1);
        await user.press(screen.getByRole('button', { name: 'Corrigir registro' }));
        expect(onLog).toHaveBeenCalledWith(today, 0);
    });
});

describe('formatação das refeições', () => {
    it('quantidade, macros e diferença com sinal', () => {
        expect(amountText({ amount: 37.5, baseUnit: 'G', measureName: 'Colher', measureCount: 1.5 })).toBe(
            '1,5 Colher (37,5 g)',
        );
        expect(amountText({ amount: 250, baseUnit: 'ML' })).toBe('250 ml');
        expect(macrosText(undefined)).toBe('0 kcal · P 0,0 g · C 0,0 g · G 0,0 g');
        expect(differenceText(amounts(10, 0, -1.25, 2))).toBe('+10,0 kcal · P 0,0 g · C -1,3 g · G +2,0 g');
    });
});
