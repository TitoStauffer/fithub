// Gerado por scripts/generate.mjs a partir de openapi/client-service.json (DEV-0022). Não edite à mão.

export interface paths {
    "/clients/me/profile": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Perfil da conta autenticada */
        get: operations["mine"];
        /** Cria ou atualiza o perfil da conta autenticada */
        put: operations["save"];
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
        ClientProfile: {
            /**
             * Format: int32
             * @description Idade em anos completos, calculada a partir da data de nascimento.
             */
            age?: number;
            /** Format: date */
            birthDate?: string;
            /** @enum {string} */
            gender?: "FEMALE" | "MALE" | "OTHER" | "PREFER_NOT_TO_SAY";
            /** @enum {string} */
            goal?: "WEIGHT_LOSS" | "HYPERTROPHY" | "CONDITIONING" | "HEALTH" | "OTHER";
            /** Format: int32 */
            heightCm?: number;
            name?: string;
        };
        ClientProfileRequest: {
            /** Format: date */
            birthDate: string;
            /** @enum {string} */
            gender: "FEMALE" | "MALE" | "OTHER" | "PREFER_NOT_TO_SAY";
            /** @enum {string} */
            goal: "WEIGHT_LOSS" | "HYPERTROPHY" | "CONDITIONING" | "HEALTH" | "OTHER";
            /** Format: int32 */
            heightCm: number;
            name: string;
        };
        ProblemDetail: {
            detail?: string;
            /** Format: uri */
            instance?: string;
            properties?: {
                [key: string]: unknown;
            };
            /** Format: int32 */
            status?: number;
            title?: string;
            /** Format: uri */
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
    mine: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Perfil da conta autenticada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientProfile"];
                };
            };
            /** @description Sem token válido. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description A conta ainda não tem perfil. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientProfile"];
                };
            };
        };
    };
    save: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ClientProfileRequest"];
            };
        };
        responses: {
            /** @description Perfil atualizado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientProfile"];
                };
            };
            /** @description Perfil criado. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientProfile"];
                };
            };
            /** @description Campo inválido; errors[] traz o campo e a mensagem. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Sem token válido. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
}
