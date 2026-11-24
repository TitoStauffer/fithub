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
    "/exercises": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Busca no catálogo e nos exercícios próprios do personal
         * @description Filtros opcionais, combinados (E lógico). O nome busca por trecho, sem diferenciar maiúsculas nem acentos. Página a partir de 0, com 20 itens por padrão e até 50.
         */
        get: operations["search"];
        put?: never;
        /** Cria um exercício próprio do personal */
        post: operations["create"];
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
        /** Edita um exercício próprio do personal */
        put: operations["update"];
        post?: never;
        /**
         * Arquiva um exercício próprio do personal
         * @description Não apaga: as fichas antigas continuam mostrando o exercício, que sai da busca. Arquivar de novo não muda nada.
         */
        delete: operations["archive"];
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
            /**
             * Format: date-time
             * @description Quando o dono arquivou o exercício próprio; presente só se arquivado.
             */
            archivedAt?: string;
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
            /** @description Autor da mídia importada; presente só quando há mídia. */
            mediaAuthor?: string;
            /** @description Licença da mídia importada; presente só quando há mídia. */
            mediaLicense?: string;
            source?: string;
        };
        /** @description Exercício próprio, com as mesmas regras do modelo. */
        ExerciseInput: {
            /** @enum {string} */
            equipment: "BARBELL" | "DUMBBELL" | "KETTLEBELL" | "MACHINE" | "CABLE" | "BAND" | "BODYWEIGHT" | "OTHER";
            /** @description Até 4000 caracteres. */
            instructions?: string | null;
            /** @description Vídeo ou imagem da execução, https de até 500 caracteres. */
            mediaUrl?: string | null;
            /** @description Um ou mais grupos musculares. */
            muscleGroups: ("CHEST" | "BACK" | "LOWER_BACK" | "SHOULDERS" | "BICEPS" | "TRICEPS" | "FOREARMS" | "ABS" | "GLUTES" | "QUADRICEPS" | "HAMSTRINGS" | "ADDUCTORS" | "CALVES" | "NECK" | "FULL_BODY")[];
            /** @description De 2 a 120 caracteres. */
            name: string;
        };
        /** @description Uma página da busca, ordenada por nome. */
        ExercisePage: {
            items?: components["schemas"]["Exercise"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /**
             * Format: int64
             * @description Quantos exercícios atendem à busca, somando todas as páginas.
             */
            total?: number;
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
            /** @description Campos inválidos; presente só nas respostas 400. */
            errors?: components["schemas"]["ProblemFieldError"][] | null;
            instance?: string | null;
            /** Format: int32 */
            status?: number;
            title?: string;
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
    search: {
        parameters: {
            query?: {
                /** @description Trecho do nome. */
                name?: string;
                muscleGroup?: "CHEST" | "BACK" | "LOWER_BACK" | "SHOULDERS" | "BICEPS" | "TRICEPS" | "FOREARMS" | "ABS" | "GLUTES" | "QUADRICEPS" | "HAMSTRINGS" | "ADDUCTORS" | "CALVES" | "NECK" | "FULL_BODY";
                equipment?: "BARBELL" | "DUMBBELL" | "KETTLEBELL" | "MACHINE" | "CABLE" | "BAND" | "BODYWEIGHT" | "OTHER";
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Página da busca. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExercisePage"];
                };
            };
            /** @description Filtro fora da lista, página negativa ou tamanho fora de 1 a 50; o campo vem em errors. */
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
            /** @description A conta não é personal. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ExerciseInput"];
            };
        };
        responses: {
            /** @description Exercício criado; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Exercise"];
                };
            };
            /** @description Campo fora das regras; o campo vem em errors. */
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
            /** @description A conta não é personal. */
            403: {
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
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                exerciseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ExerciseInput"];
            };
        };
        responses: {
            /** @description Exercício editado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Exercise"];
                };
            };
            /** @description Campo fora das regras; o campo vem em errors. */
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
            /** @description A conta não é personal. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O exercício não existe, é de outro personal ou do catálogo. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O exercício foi arquivado e não muda mais. */
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
    archive: {
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
            /** @description Exercício arquivado. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description O exercício não existe, é de outro personal ou do catálogo. */
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
