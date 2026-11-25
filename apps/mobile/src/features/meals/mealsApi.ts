import { randomUUID } from 'expo-crypto';

import {
    confirmMealPhoto,
    fetchMealPlanToday,
    logMeal,
    putToPresignedUrl,
    requestMealPhotoUpload,
    type MealItem,
    type MealLog,
    type MealLogInput,
    type MealPhotoUpload,
    type MealPlanToday,
    type NutrientAmounts,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que as refeições de hoje precisam da API; injetável nos testes (DEV-0089). */
export interface MealsApi {
    /** O plano publicado com os registros de hoje; `null` quando ainda não há plano. */
    today(): Promise<MealPlanToday | null>;
    /** O id do registro vem do aparelho: repetir o envio corrige, não duplica (DEV-0090). */
    log(logId: string, body: MealLogInput): Promise<MealLog>;
    requestPhotoUpload(logId: string, contentType: string): Promise<MealPhotoUpload>;
    /** Lê o arquivo local e envia para a URL pré-assinada, sem o token. */
    uploadPhoto(uploadUrl: string, fileUri: string, contentType: string): Promise<void>;
    confirmPhoto(logId: string, uploadId: string): Promise<MealLog>;
}

export function mealsApiFrom(apiFetch: ApiFetch, fetchImpl: typeof fetch = fetch): MealsApi {
    return {
        today: () => fetchMealPlanToday(apiFetch),
        log: (logId, body) => logMeal(apiFetch, logId, body),
        requestPhotoUpload: (logId, contentType) => requestMealPhotoUpload(apiFetch, logId, contentType),
        uploadPhoto: async (uploadUrl, fileUri, contentType) => {
            // O arquivo local vira Blob pelo próprio fetch do React Native, sem passar pela rede.
            const file = await (await fetchImpl(fileUri)).blob();
            await putToPresignedUrl(uploadUrl, file, contentType, fetchImpl);
        },
        confirmPhoto: (logId, uploadId) => confirmMealPhoto(apiFetch, logId, uploadId),
    };
}

/** Id novo de registro, gerado no aparelho. */
export const newLogId = () => randomUUID();

/** Os estados do registro, na ordem dos botões. */
export const LOG_STATUSES = [
    { value: 'DONE', label: 'Feita' },
    { value: 'PARTIAL', label: 'Parcial' },
    { value: 'SKIPPED', label: 'Não feita' },
] as const;

export type LogStatus = (typeof LOG_STATUSES)[number]['value'];

export const MAX_DEVIATION = 500;

/** O estado do registro na tela. */
export const LOG_STATUS_LABELS: Record<string, string> = {
    DONE: 'feita',
    PARTIAL: 'parcial',
    SKIPPED: 'não feita',
};

const decimal = (value: number | undefined) =>
    (value ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 1 });

const oneDecimal = (value: number | undefined) =>
    (value ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export const unitOf = (baseUnit: string | undefined) => (baseUnit === 'ML' ? 'ml' : 'g');

/** A quantidade como a nutricionista prescreveu: "100 g" ou, com a medida caseira, "2 Concha (50 g)". */
export function amountText(item: MealItem): string {
    const amount = `${decimal(item.amount)} ${unitOf(item.baseUnit)}`;
    return item.measureName ? `${decimal(item.measureCount)} ${item.measureName} (${amount})` : amount;
}

/** Os macros em uma linha: "128 kcal · P 2,5 g · C 28,1 g · G 0,2 g". */
export function macrosText(amounts: NutrientAmounts | undefined): string {
    return `${decimal(amounts?.energyKcal)} kcal · P ${oneDecimal(amounts?.proteinG)} g · C ${oneDecimal(
        amounts?.carbsG,
    )} g · G ${oneDecimal(amounts?.fatG)} g`;
}

const signed = (value: number | undefined) => {
    const number = value ?? 0;
    return `${number > 0 ? '+' : ''}${oneDecimal(number)}`;
};

/** A diferença da alternativa para o principal, com sinal. */
export function differenceText(amounts: NutrientAmounts | undefined): string {
    return `${signed(amounts?.energyKcal)} kcal · P ${signed(amounts?.proteinG)} g · C ${signed(
        amounts?.carbsG,
    )} g · G ${signed(amounts?.fatG)} g`;
}
