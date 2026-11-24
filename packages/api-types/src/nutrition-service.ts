// Gerado por scripts/generate.mjs a partir de openapi/nutrition-service.json (DEV-0022). Não edite à mão.

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
    "/foods/{foodId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Um alimento da TACO ou próprio da nutricionista */
        get: operations["viewFood"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/foods/{foodId}/measures": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cadastra uma medida caseira da nutricionista no alimento */
        post: operations["addFoodMeasure"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/foods/{foodId}/measures/{measureId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Apaga uma medida caseira da nutricionista */
        delete: operations["removeFoodMeasure"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Alimento, com os valores por 100 da unidade base (100 g ou 100 ml). */
        Food: {
            /**
             * @description G (gramas) ou ML (mililitros).
             * @enum {string}
             */
            baseUnit?: "G" | "ML";
            carbsG?: number;
            energyKcal?: number;
            fatG?: number;
            /** @description Ausente quando a fonte não traz. */
            fiberG?: number;
            /** Format: uuid */
            id?: string;
            /** @enum {string} */
            kind?: "TACO" | "OWN";
            /** @description As medidas caseiras da nutricionista neste alimento (só as dela). */
            measures?: components["schemas"]["FoodMeasure"][];
            name?: string;
            proteinG?: number;
            /** @description Presente só no item importado. */
            source?: components["schemas"]["FoodSource"];
            vegan?: boolean;
        };
        /** @description Medida caseira da nutricionista e quanto vale na unidade base. */
        FoodMeasure: {
            /** @description Gramas (ou ml) de uma medida. */
            amount?: number;
            /** Format: uuid */
            id?: string;
            name?: string;
        };
        /** @description Medida caseira nova. */
        FoodMeasureInput: {
            /** @description De 0,1 a 2000 g (ou ml). */
            amount: number;
            /** @description De 2 a 40 caracteres. */
            name: string;
        };
        /** @description Origem do item importado, para o crédito. */
        FoodSource: {
            externalId?: string;
            name?: string;
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
            /** @description A conta não é nutricionista. */
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
    viewFood: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                foodId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Alimento. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Food"];
                };
            };
            /** @description A conta não é nutricionista. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O alimento não existe ou é próprio de outra nutricionista. */
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
    addFoodMeasure: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                foodId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FoodMeasureInput"];
            };
        };
        responses: {
            /** @description Medida cadastrada; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FoodMeasure"];
                };
            };
            /** @description Nome ou valor fora das regras, nome repetido ou mais de 10 medidas; o campo vem em errors. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A conta não é nutricionista. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O alimento não existe ou é próprio de outra nutricionista. */
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
    removeFoodMeasure: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                foodId: string;
                measureId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Medida apagada. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description A conta não é nutricionista. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A medida não existe, é de outro alimento ou de outra nutricionista. */
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
