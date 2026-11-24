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
    "/clients/{clientId}/food-restrictions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As restrições alimentares do paciente vinculado */
        get: operations["listFoodRestrictions"];
        put?: never;
        /** Registra uma restrição alimentar ou preferência do paciente */
        post: operations["createFoodRestriction"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/{clientId}/food-restrictions/{restrictionId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Edita a restrição alimentar do paciente */
        put: operations["updateFoodRestriction"];
        post?: never;
        /** Apaga a restrição alimentar do paciente */
        delete: operations["removeFoodRestriction"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/{clientId}/meal-plans": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Os planos alimentares que a nutricionista criou para o paciente vinculado */
        get: operations["listMealPlans"];
        put?: never;
        /** Cria o plano alimentar do paciente vinculado, em rascunho */
        post: operations["createMealPlan"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/foods": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Busca na TACO e nos alimentos próprios da nutricionista
         * @description Filtros opcionais, combinados (E lógico). O nome busca por trecho, sem diferenciar maiúsculas nem acentos. Os arquivados ficam de fora. Página a partir de 0, com 20 itens por padrão e até 50.
         */
        get: operations["searchFoods"];
        put?: never;
        /** Cadastra um alimento ou receita própria da nutricionista */
        post: operations["createOwnFood"];
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
        /** Edita o alimento ou a receita própria (a receita é recalculada) */
        put: operations["updateOwnFood"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/foods/{foodId}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Arquiva o alimento próprio: sai da busca, mas continua nos planos */
        post: operations["archiveOwnFood"];
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
    "/me/food-restrictions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As restrições alimentares do paciente autenticado */
        get: operations["listMyFoodRestrictions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/meal-plans/{planId}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As versões publicadas do plano alimentar que o paciente recebeu */
        get: operations["listMyMealPlanVersions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/meal-plans/{planId}/versions/{number}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Uma versão publicada do plano alimentar que o paciente recebeu, com os macros */
        get: operations["getMyMealPlanVersion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/meal-plans/{planId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** O plano alimentar, para a autora (mesmo depois do fim do vínculo) */
        get: operations["getMealPlan"];
        /** Edita o plano alimentar: o rascunho no lugar; o publicado vira a versão seguinte */
        put: operations["updateMealPlan"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/meal-plans/{planId}/publish": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Publica o rascunho; o plano ativo anterior do paciente fica substituído */
        post: operations["publishMealPlan"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/meal-plans/{planId}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As versões do plano alimentar, para a autora */
        get: operations["listMealPlanVersions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/meal-plans/{planId}/versions/{number}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Uma versão do plano alimentar, com os macros, para a autora */
        get: operations["getMealPlanVersion"];
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
        /** @description Alimento, com os valores por 100 da unidade base (100 g ou 100 ml). */
        Food: {
            /** @description Se a dona arquivou o próprio: sai da busca, mas continua nos planos. */
            archived?: boolean;
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
            /** @description Presente só na receita. */
            recipe?: components["schemas"]["Recipe"];
            /** @description Presente só no item importado. */
            source?: components["schemas"]["FoodSource"];
            vegan?: boolean;
        };
        /** @description Alimento próprio. Com recipe, os valores por 100 g e a etiqueta vegano saem dos ingredientes. */
        FoodInput: {
            /**
             * @description G ou ML; a receita é sempre em gramas.
             * @enum {string}
             */
            baseUnit?: "G" | "ML";
            /** @description Por 100, de 0 a 100 g; ignorado na receita. */
            carbsG?: number;
            /** @description Por 100, de 0 a 900 kcal; ignorado na receita. */
            energyKcal?: number;
            /** @description Por 100, de 0 a 100 g; ignorado na receita. */
            fatG?: number;
            /** @description Por 100, opcional. */
            fiberG?: number;
            /** @description De 2 a 120 caracteres. */
            name: string;
            /** @description Por 100, de 0 a 100 g; ignorado na receita. */
            proteinG?: number;
            recipe?: components["schemas"]["RecipeInput"];
            /** @description Ignorado na receita: vegana só se todos os ingredientes forem. */
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
        /** @description Página da busca de alimentos. */
        FoodPage: {
            items?: components["schemas"]["FoodSummary"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /**
             * Format: int64
             * @description Quantos alimentos atendem à busca, somando todas as páginas.
             */
            total?: number;
        };
        /** @description Restrição alimentar ou preferência do paciente. */
        FoodRestriction: {
            /** Format: uuid */
            clientId?: string;
            /** Format: date-time */
            createdAt?: string;
            description?: string;
            /** Format: uuid */
            id?: string;
            /**
             * @description ALLERGY e INTOLERANCE geram alerta no plano; PREFERENCE não.
             * @enum {string}
             */
            kind?: "ALLERGY" | "INTOLERANCE" | "PREFERENCE";
            /** @description Termos procurados no nome dos alimentos. */
            terms?: string[];
            /** Format: date-time */
            updatedAt?: string;
        };
        /** @description Tipo, descrição (2 a 200) e de 1 a 10 termos (2 a 40 cada). */
        FoodRestrictionInput: {
            description: string;
            /** @enum {string} */
            kind: "ALLERGY" | "INTOLERANCE" | "PREFERENCE";
            terms: string[];
        };
        /** @description Origem do item importado, para o crédito. */
        FoodSource: {
            externalId?: string;
            name?: string;
        };
        /** @description Alimento no resultado da busca, com os valores por 100. */
        FoodSummary: {
            /** @enum {string} */
            baseUnit?: "G" | "ML";
            carbsG?: number;
            energyKcal?: number;
            fatG?: number;
            fiberG?: number;
            /** Format: uuid */
            id?: string;
            /** @enum {string} */
            kind?: "TACO" | "OWN";
            name?: string;
            proteinG?: number;
            /** @description Se é uma receita da nutricionista. */
            recipe?: boolean;
            vegan?: boolean;
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
        /** @description Refeição do plano, com os alimentos e a soma dos macros. */
        Meal: {
            items?: components["schemas"]["MealItem"][];
            name?: string;
            note?: string;
            /** Format: int32 */
            position?: number;
            /** @description Horário sugerido (HH:MM). */
            suggestedTime?: string;
            total?: components["schemas"]["NutrientAmounts"];
        };
        /** @description Nome (2 a 60), horário opcional (HH:MM), observação e até 30 alimentos. */
        MealInput: {
            items?: components["schemas"]["MealItemInput"][];
            name: string;
            note?: string;
            /**
             * @description HH:MM
             * @example 07:30
             */
            suggestedTime?: string;
        };
        /** @description Alimento da refeição, com a quantidade e os macros dela. */
        MealItem: {
            /** @description Até 2 trocas possíveis: a secundária e a terciária, na ordem. */
            alternatives?: components["schemas"]["MealItemAlternative"][];
            /** @description Quantidade na unidade base. */
            amount?: number;
            /**
             * @description G (gramas) ou ML (mililitros).
             * @enum {string}
             */
            baseUnit?: "G" | "ML";
            /** Format: uuid */
            foodId?: string;
            foodName?: string;
            measureCount?: number;
            /**
             * Format: uuid
             * @description A medida caseira usada para exibir.
             */
            measureId?: string;
            measureName?: string;
            nutrients?: components["schemas"]["NutrientAmounts"];
        };
        /** @description Troca possível do alimento, com a diferença para o principal. */
        MealItemAlternative: {
            /** @description Quantidade na unidade base. */
            amount?: number;
            /**
             * @description G (gramas) ou ML (mililitros).
             * @enum {string}
             */
            baseUnit?: "G" | "ML";
            /** @description Alternativa menos principal, com sinal: positivo é a mais na alternativa. */
            difference?: components["schemas"]["NutrientAmounts"];
            /** Format: uuid */
            foodId?: string;
            foodName?: string;
            nutrients?: components["schemas"]["NutrientAmounts"];
        };
        /** @description O alimento da alternativa e a quantidade (0,1 a 5000). */
        MealItemAlternativeInput: {
            amount: number;
            /** Format: uuid */
            foodId: string;
        };
        /** @description O alimento e a quantidade (0,1 a 5000), ou a medida caseira e quantas (a quantidade vem dela). */
        MealItemInput: {
            /** @description Até 2 alternativas, sem repetir o alimento principal nem a outra. */
            alternatives?: components["schemas"]["MealItemAlternativeInput"][];
            amount?: number;
            /** Format: uuid */
            foodId: string;
            measureCount?: number;
            /** Format: uuid */
            measureId?: string;
        };
        /** @description Plano alimentar, com as refeições e os macros do dia. */
        MealPlan: {
            /** Format: uuid */
            clientId?: string;
            /** Format: date-time */
            createdAt?: string;
            /** @description A soma das refeições. */
            day?: components["schemas"]["NutrientAmounts"];
            /** Format: uuid */
            id?: string;
            meals?: components["schemas"]["Meal"][];
            name?: string;
            note?: string;
            /** Format: date-time */
            publishedAt?: string;
            /** Format: date */
            startsOn?: string;
            /**
             * @description DRAFT, PUBLISHED (o ativo) ou REPLACED.
             * @enum {string}
             */
            status?: "DRAFT" | "PUBLISHED" | "REPLACED";
            /** Format: date-time */
            updatedAt?: string;
            /** Format: int32 */
            version?: number;
        };
        /** @description Nome (2 a 80), início, observação (até 500) e de 1 a 10 refeições. */
        MealPlanInput: {
            /**
             * Format: int32
             * @description A versão em que a edição se baseou; obrigatória para editar o plano publicado.
             */
            baseVersion?: number;
            meals: components["schemas"]["MealInput"][];
            name: string;
            note?: string;
            /** Format: date */
            startsOn: string;
        };
        /** @description Uma versão do plano: número, nome e publicação. */
        MealPlanVersionSummary: {
            name?: string;
            /** Format: int32 */
            number?: number;
            /**
             * Format: date-time
             * @description Ausente no rascunho.
             */
            publishedAt?: string;
        };
        /** @description Energia e nutrientes de uma porção, com 1 casa decimal. */
        NutrientAmounts: {
            carbsG?: number;
            energyKcal?: number;
            fatG?: number;
            /** @description Ausente quando algum alimento da soma não traz a fibra. */
            fiberG?: number;
            proteinG?: number;
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
        /** @description Ingredientes e rendimento; os valores por 100 g saem deles. */
        Recipe: {
            ingredients?: components["schemas"]["RecipeIngredient"][];
            yieldGrams?: number;
        };
        /** @description Ingrediente da receita: o alimento e a quantidade. */
        RecipeIngredient: {
            /** @description De 0,1 a 5000 g (ou ml). */
            amount: number;
            /** Format: uuid */
            foodId: string;
        };
        /** @description Ingredientes (1 a 30) e o rendimento total. */
        RecipeInput: {
            ingredients: components["schemas"]["RecipeIngredient"][];
            /** @description Em gramas, de 1 a 20000. */
            yieldGrams: number;
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
    listFoodRestrictions: {
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
            /** @description Restrições (a lista pode ser vazia). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FoodRestriction"][];
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
            /** @description Sem vínculo ativo com o paciente. */
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
    createFoodRestriction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                clientId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FoodRestrictionInput"];
            };
        };
        responses: {
            /** @description Restrição registrada; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FoodRestriction"];
                };
            };
            /** @description Campo fora das regras ou mais de 30 restrições; o campo vem em errors. */
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
            /** @description Sem vínculo ativo com o paciente. */
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
    updateFoodRestriction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                clientId: string;
                restrictionId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FoodRestrictionInput"];
            };
        };
        responses: {
            /** @description Restrição editada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FoodRestriction"];
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
            /** @description A conta não é nutricionista. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo, ou a restrição não existe ou é de outro paciente. */
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
    removeFoodRestriction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                clientId: string;
                restrictionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Restrição apagada. */
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
            /** @description Sem vínculo ativo, ou a restrição não existe ou é de outro paciente. */
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
    listMealPlans: {
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
            /** @description Planos, do mais novo para o mais antigo. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"][];
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
            /** @description Sem vínculo ativo com o paciente. */
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
    createMealPlan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                clientId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MealPlanInput"];
            };
        };
        responses: {
            /** @description Plano criado; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"];
                };
            };
            /** @description Campo fora das regras ou alimento indisponível; o campo vem em errors. */
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
            /** @description Sem vínculo ativo com o paciente. */
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
    searchFoods: {
        parameters: {
            query?: {
                /** @description Trecho do nome. */
                name?: string;
                /** @description true para só os veganos; false para só os não veganos. */
                vegan?: string;
                /** @description Página, a partir de 0. */
                page?: string;
                /** @description Itens por página, de 1 a 50. */
                size?: string;
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
                    "application/json": components["schemas"]["FoodPage"];
                };
            };
            /** @description Filtro fora das regras, página negativa ou tamanho fora de 1 a 50; o campo vem em errors. */
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
        };
    };
    createOwnFood: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FoodInput"];
            };
        };
        responses: {
            /** @description Alimento cadastrado; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Food"];
                };
            };
            /** @description Campo fora das regras ou ingrediente indisponível; o campo vem em errors. */
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
    updateOwnFood: {
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
                "application/json": components["schemas"]["FoodInput"];
            };
        };
        responses: {
            /** @description Alimento editado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Food"];
                };
            };
            /** @description Campo fora das regras ou ingrediente indisponível; o campo vem em errors. */
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
            /** @description O alimento não existe, é da TACO ou de outra nutricionista. */
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
    archiveOwnFood: {
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
            /** @description Alimento arquivado (arquivar de novo não muda nada). */
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
            /** @description O alimento não existe, é da TACO ou de outra nutricionista. */
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
    listMyFoodRestrictions: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Restrições (a lista pode ser vazia). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FoodRestriction"][];
                };
            };
            /** @description A conta não é de cliente. */
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
    listMyMealPlanVersions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Versões publicadas, da primeira para a última. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlanVersionSummary"][];
                };
            };
            /** @description A conta não é de cliente. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O plano não existe, é de outro paciente ou ainda é rascunho. */
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
    getMyMealPlanVersion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
                number: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description A versão, com as refeições e os macros. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"];
                };
            };
            /** @description A conta não é de cliente. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O plano ou a versão não existe, é de outro paciente ou ainda é rascunho. */
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
    getMealPlan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Plano com as refeições e os macros. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"];
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
            /** @description O plano não existe ou é de outra nutricionista. */
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
    updateMealPlan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MealPlanInput"];
            };
        };
        responses: {
            /** @description Plano editado (no publicado, já na versão nova). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"];
                };
            };
            /** @description Campo fora das regras ou alimento indisponível; o campo vem em errors. */
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
            /** @description O plano não existe, é de outra nutricionista ou o vínculo acabou. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O plano foi substituído, outra edição chegou antes ou ele mudou desde a leitura. */
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
    publishMealPlan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Plano publicado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"];
                };
            };
            /** @description Alguma refeição sem alimento (BR-10); o campo vem em errors. */
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
            /** @description O plano não existe, é de outra nutricionista ou o vínculo acabou. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O plano já foi publicado. */
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
    listMealPlanVersions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Versões, da primeira para a última. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlanVersionSummary"][];
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
            /** @description O plano não existe ou é de outra nutricionista. */
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
    getMealPlanVersion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                planId: string;
                number: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description A versão, com as refeições e os macros. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MealPlan"];
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
            /** @description O plano ou a versão não existe, ou o plano é de outra nutricionista. */
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
