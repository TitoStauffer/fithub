// Gerado por scripts/generate.mjs a partir de openapi/training-service.json (DEV-0022). Não edite à mão.

export interface paths {
    "/clients/{clientId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Dados básicos de um cliente com vínculo ativo */
        get: operations["client"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/exercises/{exerciseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Um exercício do catálogo ou próprio do personal */
        get: operations["exercise"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Exercício do catálogo ou próprio do personal. */
        Exercise: {
            /** @description Presente só em exercício importado; a tela mostra o crédito. */
            attribution?: components["schemas"]["ExerciseAttribution"];
            /** @enum {string} */
            equipment?: "BARBELL" | "DUMBBELL" | "KETTLEBELL" | "MACHINE" | "CABLE" | "BAND" | "BODYWEIGHT" | "OTHER";
            /** Format: uuid */
            id?: string;
            instructions?: string | null;
            /** @enum {string} */
            kind?: "CATALOG" | "OWN";
            /** @description Vídeo ou imagem da execução, sempre https. */
            mediaUrl?: string | null;
            /** @description Grupos musculares, em ordem alfabética. */
            muscleGroups?: ("CHEST" | "BACK" | "LOWER_BACK" | "SHOULDERS" | "BICEPS" | "TRICEPS" | "FOREARMS" | "ABS" | "GLUTES" | "QUADRICEPS" | "HAMSTRINGS" | "ADDUCTORS" | "CALVES" | "NECK" | "FULL_BODY")[];
            name?: string;
        };
        /** @description Origem e licença do exercício importado (ADR-010). */
        ExerciseAttribution: {
            author?: string;
            license?: string;
            source?: string;
        };
        /** @description Dados básicos do cliente vinculado. */
        LinkedClient: {
            /** Format: date */
            birthDate?: string | null;
            /** Format: uuid */
            clientId?: string;
            gender?: string | null;
            name?: string;
        };
        /** @description Erro no formato Problem Details (RFC 9457). */
        Problem: {
            detail?: string;
            instance?: string | null;
            /** Format: int32 */
            status?: number;
            title?: string;
            type?: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    client: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                clientId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Cliente vinculado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LinkedClient"];
                };
            };
            /** @description Sem token válido. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A conta não é personal. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo com o cliente (ou ele não existe). */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    exercise: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                exerciseId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Exercício. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Exercise"];
                };
            };
            /** @description Sem token válido. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A conta não é personal. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O exercício não existe ou é próprio de outro personal. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
}
