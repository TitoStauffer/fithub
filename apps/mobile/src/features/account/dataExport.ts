import {
    fetchMyClientExport,
    fetchMyNutritionExport,
    fetchMyTrainingExport,
    type ClientExport,
    type NutritionExport,
    type TrainingExport,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** As três exportações que a tela junta (DEV-0168); injetável nos testes. */
export interface DataExportApi {
    profile(): Promise<ClientExport>;
    training(): Promise<TrainingExport>;
    nutrition(): Promise<NutritionExport>;
}

export function dataExportApiFrom(apiFetch: ApiFetch): DataExportApi {
    return {
        profile: () => fetchMyClientExport(apiFetch),
        training: () => fetchMyTrainingExport(apiFetch),
        nutrition: () => fetchMyNutritionExport(apiFetch),
    };
}

/** O nome de cada parte, como a tela mostra quando ela falha. */
export const PART_LABELS = {
    profile: 'perfil, saúde, avaliações e vínculos',
    training: 'treinos',
    nutrition: 'alimentação',
} as const;

export type ExportPart = keyof typeof PART_LABELS;

/** Ou o arquivo completo, ou as partes que faltaram: nunca um arquivo incompleto como se estivesse completo. */
export type ExportResult = { complete: true; json: string } | { complete: false; missing: ExportPart[] };

/** Junta as três exportações num JSON legível, com a data em que foi gerado. */
export async function collectExport(
    api: DataExportApi,
    now: () => Date = () => new Date(),
): Promise<ExportResult> {
    const [profile, training, nutrition] = await Promise.allSettled([
        api.profile(),
        api.training(),
        api.nutrition(),
    ]);
    const missing: ExportPart[] = [];
    if (profile.status === 'rejected') missing.push('profile');
    if (training.status === 'rejected') missing.push('training');
    if (nutrition.status === 'rejected') missing.push('nutrition');
    if (profile.status === 'rejected' || training.status === 'rejected' || nutrition.status === 'rejected') {
        return { complete: false, missing };
    }
    const document = {
        exportedAt: now().toISOString(),
        profile: profile.value,
        training: training.value,
        nutrition: nutrition.value,
    };
    return { complete: true, json: JSON.stringify(document, null, 2) };
}
