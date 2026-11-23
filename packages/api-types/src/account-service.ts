// Gerado por scripts/generate.mjs a partir de openapi/account-service.json (DEV-0022). Não edite à mão.

export interface paths {
    "/professionals/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Cadastro de profissional da conta autenticada
         * @description 404 se ainda não houver.
         */
        get: operations["mine"];
        put?: never;
        /**
         * Cadastra a conta autenticada como profissional
         * @description 201 quando cria; 200 quando o mesmo cadastro já existia; 409 quando a conta já tem um cadastro diferente.
         */
        post: operations["register"];
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
        Professional: {
            /** Format: date-time */
            createdAt?: string;
            /** Format: uuid */
            id?: string;
            name?: string;
            /** Format: uuid */
            organizationId?: string;
            registration?: components["schemas"]["ProfessionalRegistration"];
            /**
             * @description PENDING enquanto o papel de profissional não chega ao token; renove a sessão.
             * @enum {string}
             */
            roleStatus?: "PENDING" | "ASSIGNED";
            /** @enum {string} */
            situation?: "DECLARED";
            /** @enum {string} */
            type?: "PERSONAL" | "NUTRITIONIST";
        };
        ProfessionalRegistration: {
            category?: string;
            /** @enum {string} */
            council?: "CREF" | "CRN";
            display?: string;
            number?: string;
            state?: string;
        };
        ProfessionalRegistrationRequest: {
            /** @description Categoria do CREF (G ou P). Não se aplica ao CRN. */
            registrationCategory?: string | null;
            registrationNumber: string;
            registrationState: string;
            /** @enum {string} */
            type: "PERSONAL" | "NUTRITIONIST";
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
            /** @description Cadastro da conta autenticada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Professional"];
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
            /** @description A conta ainda não tem cadastro de profissional. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Professional"];
                };
            };
        };
    };
    register: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfessionalRegistrationRequest"];
            };
        };
        responses: {
            /** @description O mesmo cadastro já existia. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Professional"];
                };
            };
            /** @description Cadastro criado. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Professional"];
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
            /** @description A conta já tem um cadastro com outros dados. */
            409: {
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
