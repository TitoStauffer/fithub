import type { FoodRestrictionKind } from '@/lib/nutritionApi';

/** Chave das restrições alimentares do paciente no cache. */
export const foodRestrictionsKey = (clientId: string) =>
    ['nutritionist', 'client', clientId, 'food-restrictions'] as const;

/** Nome do tipo na tela. */
export const KIND_LABELS: Record<string, string> = {
    ALLERGY: 'Alergia',
    INTOLERANCE: 'Intolerância',
    PREFERENCE: 'Preferência',
};

export const KINDS: readonly FoodRestrictionKind[] = ['ALLERGY', 'INTOLERANCE', 'PREFERENCE'];

/** Limites do nutrition-service (DEV-0080), espelhados na tela. */
export const LIMITS = {
    minDescription: 2,
    maxDescription: 200,
    maxTerms: 10,
    minTerm: 2,
    maxTerm: 40,
} as const;

export interface RestrictionForm {
    kind: FoodRestrictionKind | '';
    description: string;
    /** Os termos como a nutricionista digita, separados por vírgula. */
    terms: string;
}

/** Os termos digitados, sem espaços nas pontas e sem os vazios (vírgula sobrando não vira termo). */
export function termsOf(value: string): string[] {
    return value
        .split(',')
        .map((term) => term.trim())
        .filter((term) => term.length > 0);
}

/** Como a API: tipo obrigatório, descrição de 2 a 200, de 1 a 10 termos de 2 a 40 sem repetir (sem diferenciar caixa). */
export function validate(form: RestrictionForm): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!form.kind) errors.kind = 'Escolha o tipo.';
    const description = form.description.trim();
    if (description.length < LIMITS.minDescription || description.length > LIMITS.maxDescription) {
        errors.description = 'A descrição precisa ter de 2 a 200 caracteres.';
    }
    const terms = termsOf(form.terms);
    if (terms.length < 1 || terms.length > LIMITS.maxTerms) {
        errors.terms = 'Informe de 1 a 10 termos.';
        return errors;
    }
    const seen = new Set<string>();
    terms.forEach((term, index) => {
        if (term.length < LIMITS.minTerm || term.length > LIMITS.maxTerm) {
            errors[`terms[${index}]`] = `O termo "${term}" precisa ter de 2 a 40 caracteres.`;
        } else if (seen.has(term.toLowerCase())) {
            errors[`terms[${index}]`] = `O termo "${term}" está repetido.`;
        }
        seen.add(term.toLowerCase());
    });
    return errors;
}
