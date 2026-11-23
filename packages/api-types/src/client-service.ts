// Gerado por scripts/generate.mjs a partir de openapi/client-service.json (DEV-0022). Não edite à mão.

export interface paths {
    "/clients/me/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Perfil de saúde da conta autenticada */
        get: operations["mine_1"];
        /** Grava o perfil de saúde da conta autenticada (substitui o anterior) */
        put: operations["save_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
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
        HealthProfile: {
            conditions?: ("DIABETES" | "HYPERTENSION" | "PREGNANCY" | "RECENT_SURGERY" | "OTHER")[];
            notes?: string | null;
            /** Format: date-time */
            updatedAt?: string;
        };
        HealthProfileRequest: {
            /** @description Condições marcadas; repetidas contam uma vez. */
            conditions: ("DIABETES" | "HYPERTENSION" | "PREGNANCY" | "RECENT_SURGERY" | "OTHER")[];
            /** @description Texto livre opcional, até 1000 caracteres. */
            notes?: string;
        };
        /** @description Erro no formato Problem Details (RFC 9457). */
        Problem: {
            /** @description Mensagem para a pessoa, em português. */
            detail?: string;
            /** @description Campos inválidos; presente só nas respostas 400. */
            errors?: components["schemas"]["ProblemFieldError"][] | null;
            instance?: string | null;
            /** Format: int32 */
            status?: number;
            title?: string;
            /**
             * @description URI do tipo do erro.
             * @example about:blank
             */
            type?: string;
        };
        ProblemFieldError: {
            field?: string;
            message?: string;
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
    mine_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Perfil de saúde da conta autenticada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HealthProfile"];
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
            /** @description A conta ainda não informou os dados de saúde. */
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
    save_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HealthProfileRequest"];
            };
        };
        responses: {
            /** @description Perfil de saúde gravado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HealthProfile"];
                };
            };
            /** @description Campo inválido; errors[] traz o campo e a mensagem. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
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
            /** @description A conta ainda não tem o perfil do cliente. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
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
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A conta ainda não tem perfil. */
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
                    "application/problem+json": components["schemas"]["Problem"];
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
        };
    };
}
