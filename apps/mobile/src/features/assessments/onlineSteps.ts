import type { AssessmentRequest } from '../../lib/api';

export type Sex = 'FEMALE' | 'MALE';
export type Measure = 'neck' | 'waist' | 'hip' | 'abdomen' | 'chest' | 'arm' | 'forearm' | 'thigh' | 'calf';

/**
 * Passo a passo da avaliação online (DEV-0050, FR-17): cada circunferência com a instrução de como medir. As três
 * primeiras alimentam a estimativa da Marinha (DEV-0046), por isso vêm antes.
 */
export const MEASURES: readonly { key: Measure; label: string; howTo: string }[] = [
    {
        key: 'neck',
        label: 'Pescoço',
        howTo: 'Passe a fita logo abaixo do pomo de adão, reta e sem apertar. Olhe para a frente, com os ombros relaxados.',
    },
    {
        key: 'waist',
        label: 'Cintura',
        howTo: 'Meça na parte mais fina entre as costelas e o umbigo, no fim de uma expiração normal, sem encolher a barriga.',
    },
    {
        key: 'hip',
        label: 'Quadril',
        howTo: 'Com os pés juntos, passe a fita na parte mais larga do quadril e dos glúteos, paralela ao chão.',
    },
    {
        key: 'abdomen',
        label: 'Abdômen',
        howTo: 'Passe a fita na altura do umbigo, no fim de uma expiração normal.',
    },
    {
        key: 'chest',
        label: 'Tórax',
        howTo: 'Passe a fita na linha dos mamilos, por baixo das axilas, no fim de uma expiração normal.',
    },
    {
        key: 'arm',
        label: 'Braço',
        howTo: 'Com o braço relaxado ao lado do corpo, meça no meio, entre o ombro e o cotovelo.',
    },
    {
        key: 'forearm',
        label: 'Antebraço',
        howTo: 'Com o braço estendido e relaxado, meça na parte mais larga, logo abaixo do cotovelo.',
    },
    {
        key: 'thigh',
        label: 'Coxa',
        howTo: 'Em pé, com o peso nas duas pernas, meça logo abaixo do glúteo, na parte mais larga da coxa.',
    },
    {
        key: 'calf',
        label: 'Panturrilha',
        howTo: 'Em pé, com o peso nas duas pernas, meça na parte mais larga da panturrilha.',
    },
];

/** O que a pessoa preenche: texto, para nada se perder enquanto corrige; `skipped` guarda as medidas puladas. */
export interface OnlineForm {
    weightKg: string;
    sex?: Sex;
    measures: Partial<Record<Measure, string>>;
    skipped: Measure[];
}

export const EMPTY_ONLINE_FORM: OnlineForm = { weightKg: '', measures: {}, skipped: [] };

/** Mesma regra do client-service: número com até 1 casa decimal, vírgula ou ponto. */
export function decimal(text: string | undefined): number | undefined {
    const value = (text ?? '').trim().replace(',', '.');
    if (value === '') return undefined;
    return /^\d+(\.\d)?$/.test(value) ? Number(value) : Number.NaN;
}

export function weightError(text: string): string | undefined {
    const value = decimal(text);
    return value === undefined || Number.isNaN(value) || value < 20 || value > 400
        ? 'O peso vai de 20 a 400 kg, com até 1 casa decimal.'
        : undefined;
}

export function measureError(text: string | undefined): string | undefined {
    const value = decimal(text);
    if (value === undefined) return 'Digite a medida ou toque em "Pular esta medida".';
    return Number.isNaN(value) || value < 10 || value > 300
        ? 'De 10 a 300 cm, com até 1 casa decimal.'
        : undefined;
}

/** Medidas que a fórmula da Marinha usa para o sexo escolhido (quadril só para mulher). */
export function navyMeasures(sex: Sex | undefined): Measure[] {
    return sex === 'FEMALE' ? ['neck', 'waist', 'hip'] : ['neck', 'waist'];
}

/**
 * Por que a estimativa online não vai sair, ou `undefined` se os dados permitem calcular. A API decide de novo
 * (precisa também da altura do perfil); o aviso só antecipa o que a pessoa ainda pode corrigir.
 */
export function estimateWarning(form: OnlineForm): string | undefined {
    if (!form.sex) return 'Sem o sexo para as fórmulas, o % de gordura estimado não será calculado.';
    const missing = navyMeasures(form.sex).filter((key) => form.skipped.includes(key));
    if (missing.length === 0) return undefined;
    const labels = MEASURES.filter((measure) => missing.includes(measure.key)).map(
        (measure) => measure.label,
    );
    return `Sem ${labels.join(', ').toLowerCase()}, o % de gordura estimado não será calculado.`;
}

export function toRequest(form: OnlineForm, measuredOn: string): AssessmentRequest {
    const circumferences: Partial<Record<Measure, number>> = {};
    for (const { key } of MEASURES) {
        if (form.skipped.includes(key)) continue;
        const value = decimal(form.measures[key]);
        if (value !== undefined && !Number.isNaN(value)) circumferences[key] = value;
    }
    return {
        measuredOn,
        weightKg: decimal(form.weightKg) ?? 0,
        ...(form.sex ? { sexForFormulas: form.sex } : {}),
        ...(Object.keys(circumferences).length > 0 ? { circumferences } : {}),
    };
}

export function todayIso(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
