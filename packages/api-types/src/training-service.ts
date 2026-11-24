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
    "/clients/{clientId}/plans": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Fichas que o personal criou para o aluno vinculado, da mais nova para a mais antiga */
        get: operations["list_1"];
        put?: never;
        /** Cria uma ficha em rascunho para o aluno vinculado */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/{clientId}/restrictions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Restrições de mobilidade do aluno vinculado */
        get: operations["list"];
        put?: never;
        /** Registra uma restrição de mobilidade do aluno vinculado */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/{clientId}/restrictions/{restrictionId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Edita uma restrição de mobilidade do aluno vinculado */
        put: operations["update_2"];
        post?: never;
        /** Remove uma restrição de mobilidade do aluno vinculado */
        delete: operations["remove"];
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
        put: operations["update_1"];
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
    "/me/plans/active": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As fichas publicadas do aluno, com o próximo treino da sequência */
        get: operations["activePlans"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/plans/{planId}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As versões publicadas da ficha que o aluno recebeu */
        get: operations["versions_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/plans/{planId}/versions/{number}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Uma versão publicada da ficha que o aluno recebeu */
        get: operations["version_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/restrictions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As restrições de mobilidade do próprio aluno */
        get: operations["mine"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/sessions/{sessionId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** O treino com o prescrito, as séries feitas e o último treino de cada exercício */
        get: operations["viewSession"];
        /**
         * Inicia o próximo treino da ficha
         * @description O id é gerado pelo app: repetir com o mesmo id devolve a mesma sessão.
         */
        put: operations["start"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/sessions/{sessionId}/feedback": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Comentário e sensação do treino
         * @description Substitui o que havia. Vale no mesmo prazo da correção das séries.
         */
        put: operations["recordFeedback"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/sessions/{sessionId}/finish": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Finaliza o treino; a sequência avança */
        post: operations["finish"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/sessions/{sessionId}/sets/{setId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Registra ou corrige uma série
         * @description O id é gerado pelo app: repetir corrige a mesma série em vez de criar outra.
         */
        put: operations["recordSet"];
        post?: never;
        /** Apaga uma série, no mesmo prazo da correção */
        delete: operations["removeSet"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/plans/expiring": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fichas publicadas que vencem no período ou já venceram
         * @description Só as do próprio personal e de alunos com vínculo ativo, pela data de vencimento.
         */
        get: operations["expiring"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/plans/{planId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Uma ficha do personal
         * @description O autor lê a ficha mesmo depois que o vínculo acaba (BR-05).
         */
        get: operations["view"];
        /** Edita a ficha: o rascunho no lugar; a publicada vira a versão seguinte */
        put: operations["update"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/plans/{planId}/publish": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Publica a ficha; a que estava ativa para o aluno fica substituída */
        post: operations["publish"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/plans/{planId}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** As versões da ficha, da primeira para a última */
        get: operations["versions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/plans/{planId}/versions/{number}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Uma versão da ficha, pelo número */
        get: operations["version"];
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
        /** @description Ficha publicada do aluno com o próximo treino da sequência. */
        ActivePlan: {
            divisions?: components["schemas"]["ClientWorkoutDivision"][];
            /** @description A validade passou; o aluno continua vendo e registrando. */
            expired?: boolean;
            /** Format: date */
            expiresOn?: string;
            name?: string;
            /**
             * Format: int32
             * @description Posição do próximo treino, a partir de 0.
             */
            nextDivision?: number;
            nextDivisionLetter?: string;
            /**
             * Format: uuid
             * @description Sessão aberta nessa ficha, para continuar o treino; ausente se não houver.
             */
            openSessionId?: string;
            /** Format: uuid */
            planId?: string;
            /** Format: date */
            startsOn?: string;
            /** Format: int32 */
            version?: number;
        };
        /** @description Séries do último treino com uma alternativa do item. */
        AlternativeLast: {
            /** Format: uuid */
            exerciseId?: string;
            sets?: components["schemas"]["RecordedSet"][];
        };
        /** @description O exercício como o aluno vê: nome, mídia e crédito. */
        ClientExercise: {
            /** @description Presente só em exercício importado; a tela mostra o crédito. */
            attribution?: components["schemas"]["ExerciseAttribution"];
            /** Format: uuid */
            id?: string;
            /** @description Vídeo ou imagem, https; ausente se não houver. */
            mediaUrl?: string;
            name?: string;
        };
        ClientWorkoutDivision: {
            items?: components["schemas"]["ClientWorkoutItem"][];
            letter?: string;
            name?: string | null;
            /** Format: int32 */
            position?: number;
        };
        /** @description Exercício prescrito, com o exercício e as alternativas. */
        ClientWorkoutItem: {
            alternatives?: components["schemas"]["ClientExercise"][];
            exercise?: components["schemas"]["ClientExercise"];
            loadKg?: number | null;
            note?: string | null;
            /** Format: int32 */
            repsMax?: number;
            /**
             * Format: int32
             * @description Igual ao máximo quando as repetições são fixas.
             */
            repsMin?: number;
            /** Format: int32 */
            restSeconds?: number | null;
            /** Format: int32 */
            sets?: number;
            /** @enum {string|null} */
            technique?: "BI_SET" | "TRI_SET" | "DROP_SET" | "REST_PAUSE" | null;
        };
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
        /** @description Ficha publicada que vence no período ou já venceu. */
        ExpiringPlan: {
            /** Format: uuid */
            clientId?: string;
            /** @description Nome do aluno, quando já chegou à projeção local. */
            clientName?: string | null;
            /** Format: date */
            expiresOn?: string;
            name?: string;
            /** Format: uuid */
            planId?: string;
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
        /** @description Restrição de mobilidade do aluno. */
        MobilityRestriction: {
            /** Format: date-time */
            createdAt?: string;
            description?: string;
            /** Format: uuid */
            id?: string;
            /** @enum {string} */
            region?: "CERVICAL" | "SHOULDER" | "ELBOW" | "WRIST_HAND" | "THORACIC" | "LUMBAR" | "HIP" | "KNEE" | "ANKLE_FOOT";
            /** Format: date-time */
            updatedAt?: string;
        };
        /** @description Região do corpo e descrição da restrição. */
        MobilityRestrictionInput: {
            /** @description De 1 a 500 caracteres. */
            description: string;
            /**
             * @description Região do corpo.
             * @enum {string}
             */
            region: "CERVICAL" | "SHOULDER" | "ELBOW" | "WRIST_HAND" | "THORACIC" | "LUMBAR" | "HIP" | "KNEE" | "ANKLE_FOOT";
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
        /** @description Série registrada. */
        RecordedSet: {
            /** Format: uuid */
            exerciseId?: string;
            /** Format: uuid */
            id?: string;
            loadKg?: number;
            /** Format: int32 */
            reps?: number;
            /** Format: int32 */
            setNumber?: number;
        };
        /** @description Carga e repetições reais de uma série. */
        RecordedSetInput: {
            /**
             * Format: uuid
             * @description Exercício feito: vazio para o do treino, ou uma alternativa prevista.
             */
            exerciseId?: string | null;
            /**
             * Format: int32
             * @description Posição do exercício na divisão.
             */
            itemPosition: number;
            /** @description De 0 a 1000 kg, em passos de 0,25. */
            loadKg: number;
            /**
             * Format: int32
             * @description De 0 a 300.
             */
            reps: number;
            /**
             * Format: int32
             * @description Número da série, de 1 a 20.
             */
            setNumber: number;
        };
        /** @description Como foi o treino. */
        SessionFeedback: {
            /** @description Até 500 caracteres. */
            comment?: string | null;
            /**
             * Format: int32
             * @description De 1 (muito fácil) a 5 (exaustivo).
             */
            feeling?: number | null;
        };
        /** @description Exercício do treino com o prescrito, o feito e o último treino. */
        SessionItem: {
            alternatives?: components["schemas"]["ClientExercise"][];
            /** @description Séries do último treino com cada alternativa, para pré-preencher depois da troca. */
            alternativesLast?: components["schemas"]["AlternativeLast"][];
            exercise?: components["schemas"]["ClientExercise"];
            /** @description Séries do último treino com o exercício, para pré-preencher. */
            last?: components["schemas"]["RecordedSet"][];
            loadKg?: number | null;
            note?: string | null;
            /** Format: int32 */
            position?: number;
            /** @description Séries já registradas neste treino. */
            recorded?: components["schemas"]["RecordedSet"][];
            /** Format: int32 */
            repsMax?: number;
            /** Format: int32 */
            repsMin?: number;
            /** Format: int32 */
            restSeconds?: number | null;
            /** Format: int32 */
            sets?: number;
            /** @enum {string|null} */
            technique?: "BI_SET" | "TRI_SET" | "DROP_SET" | "REST_PAUSE" | null;
        };
        /** @description O treino em andamento (ou já feito), na versão da ficha da sessão. */
        SessionView: {
            /** @description Comentário do aluno sobre o treino. */
            comment?: string | null;
            /** @description Se ainda dá para registrar e corrigir: até o fim do dia do treino. */
            correctable?: boolean;
            divisionLetter?: string;
            divisionName?: string | null;
            /** Format: int32 */
            divisionPosition?: number;
            /**
             * Format: int32
             * @description Sensação de 1 (muito fácil) a 5 (exaustivo).
             */
            feeling?: number | null;
            /**
             * Format: date-time
             * @description Quando o aluno finalizou; ausente enquanto está aberta.
             */
            finishedAt?: string;
            /** Format: uuid */
            id?: string;
            items?: components["schemas"]["SessionItem"][];
            /** Format: uuid */
            planId?: string;
            /** Format: date-time */
            startedAt?: string;
            /** Format: int32 */
            versionNumber?: number;
        };
        /** @description Divisão da ficha, na ordem da sequência. */
        WorkoutDivision: {
            /** @description Exercícios prescritos, na ordem. */
            items?: components["schemas"]["WorkoutItem"][];
            /** @description Letra da posição: A, B, C... */
            letter?: string;
            name?: string | null;
        };
        WorkoutDivisionInput: {
            /** @description Até 30 exercícios, na ordem. */
            items?: components["schemas"]["WorkoutItemInput"][] | null;
            /** @description Até 60 caracteres. */
            name?: string | null;
        };
        /** @description Exercício prescrito na divisão. */
        WorkoutItem: {
            /** @description Exercícios alternativos, na ordem. */
            alternatives?: string[];
            /** Format: uuid */
            exerciseId?: string;
            /** @description Carga sugerida em kg. */
            loadKg?: number | null;
            note?: string | null;
            /** Format: int32 */
            repsMax?: number;
            /**
             * Format: int32
             * @description Igual ao máximo quando as repetições são fixas.
             */
            repsMin?: number;
            /**
             * Format: int32
             * @description Descanso entre as séries, em segundos.
             */
            restSeconds?: number | null;
            /** Format: int32 */
            sets?: number;
            /** @enum {string|null} */
            technique?: "BI_SET" | "TRI_SET" | "DROP_SET" | "REST_PAUSE" | null;
        };
        /** @description Exercício prescrito, com as regras do modelo. */
        WorkoutItemInput: {
            /** @description Até 3 exercícios, diferentes do principal. */
            alternatives?: string[] | null;
            /** Format: uuid */
            exerciseId: string;
            /** @description De 0 a 1000 kg, em passos de 0,5. */
            loadKg?: number | null;
            /** @description Até 500 caracteres. */
            note?: string | null;
            /**
             * Format: int32
             * @description Máximo da faixa (8–12); vazio para repetições fixas.
             */
            repsMax?: number | null;
            /**
             * Format: int32
             * @description Repetições, de 1 a 100.
             */
            repsMin: number;
            /**
             * Format: int32
             * @description De 0 a 600 segundos.
             */
            restSeconds?: number | null;
            /**
             * Format: int32
             * @description De 1 a 20.
             */
            sets: number;
            /** @enum {string|null} */
            technique?: "BI_SET" | "TRI_SET" | "DROP_SET" | "REST_PAUSE" | null;
        };
        /** @description Ficha de treino, com o conteúdo da versão atual. */
        WorkoutPlan: {
            /** @description Alertas de restrição de mobilidade; vazio sem vínculo ativo ou sem restrição. */
            alerts?: components["schemas"]["WorkoutPlanAlert"][];
            /** Format: uuid */
            clientId?: string;
            /** Format: date-time */
            createdAt?: string;
            divisions?: components["schemas"]["WorkoutDivision"][];
            /**
             * Format: date
             * @description Início mais a validade.
             */
            expiresOn?: string;
            /** Format: uuid */
            id?: string;
            name?: string;
            /**
             * Format: date-time
             * @description Quando a versão atual foi publicada; ausente no rascunho.
             */
            publishedAt?: string;
            /** Format: date */
            startsOn?: string;
            /** @enum {string} */
            status?: "DRAFT" | "PUBLISHED" | "REPLACED";
            /** Format: date-time */
            updatedAt?: string;
            /** Format: int32 */
            validityWeeks?: number;
            /** Format: int32 */
            version?: number;
        };
        /** @description Exercício da ficha que envolve uma região restrita do aluno. Alerta, não bloqueio (BR-11). */
        WorkoutPlanAlert: {
            /** @description Se o exercício é uma alternativa do item. */
            alternative?: boolean;
            /** @description Descrição da restrição, como o personal registrou. */
            description?: string;
            divisionLetter?: string;
            /**
             * Format: int32
             * @description Posição da divisão, a partir de 0.
             */
            divisionPosition?: number;
            /** Format: uuid */
            exerciseId?: string;
            /**
             * Format: int32
             * @description Posição do item na divisão, a partir de 0.
             */
            itemPosition?: number;
            /** @enum {string} */
            region?: "CERVICAL" | "SHOULDER" | "ELBOW" | "WRIST_HAND" | "THORACIC" | "LUMBAR" | "HIP" | "KNEE" | "ANKLE_FOOT";
            /** Format: uuid */
            restrictionId?: string;
        };
        /** @description Cabeçalho e divisões da ficha. Na ficha publicada, a edição gera a versão seguinte. */
        WorkoutPlanInput: {
            /**
             * Format: int32
             * @description Versão em que a edição se baseou; obrigatória ao editar a ficha publicada.
             */
            baseVersion?: number | null;
            /** @description De 1 a 7 divisões, na ordem. */
            divisions: components["schemas"]["WorkoutDivisionInput"][];
            /** @description De 2 a 80 caracteres. */
            name: string;
            /** Format: date */
            startsOn: string;
            /**
             * Format: int32
             * @description De 1 a 52 semanas.
             */
            validityWeeks: number;
        };
        /** @description O conteúdo de uma versão da ficha; a publicada nunca muda. */
        WorkoutPlanVersion: {
            divisions?: components["schemas"]["WorkoutDivision"][];
            /** Format: date */
            expiresOn?: string;
            name?: string;
            /** Format: int32 */
            number?: number;
            /**
             * Format: date-time
             * @description Quando foi publicada; ausente no rascunho.
             */
            publishedAt?: string;
            /** Format: date */
            startsOn?: string;
            /** Format: int32 */
            validityWeeks?: number;
        };
        /** @description Uma versão da ficha, sem o conteúdo. */
        WorkoutPlanVersionSummary: {
            name?: string;
            /** Format: int32 */
            number?: number;
            /**
             * Format: date-time
             * @description Quando foi publicada; ausente no rascunho.
             */
            publishedAt?: string;
        };
        /** @description Sessão de treino: a divisão da ficha, na versão que estava ativa. */
        WorkoutSession: {
            /** Format: int32 */
            divisionPosition?: number;
            /**
             * Format: date-time
             * @description Quando o aluno finalizou; ausente enquanto está aberta.
             */
            finishedAt?: string;
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            planId?: string;
            /** Format: date-time */
            startedAt?: string;
        };
        WorkoutSessionInput: {
            /** Format: uuid */
            planId: string;
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
    list_1: {
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
            /** @description Fichas. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlan"][];
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
            /** @description Sem vínculo ativo com o aluno. */
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
    create_2: {
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
                "application/json": components["schemas"]["WorkoutPlanInput"];
            };
        };
        responses: {
            /** @description Ficha criada; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlan"];
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
            /** @description A conta não é personal. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo com o aluno. */
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
    list: {
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
            /** @description Restrições, da mais antiga para a mais nova. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MobilityRestriction"][];
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
            /** @description Sem vínculo ativo com o aluno. */
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
    create_1: {
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
                "application/json": components["schemas"]["MobilityRestrictionInput"];
            };
        };
        responses: {
            /** @description Restrição registrada; o endereço vem em Location. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MobilityRestriction"];
                };
            };
            /** @description Região fora da lista ou descrição fora do tamanho; o campo vem em errors. */
            400: {
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
            /** @description Sem vínculo ativo com o aluno. */
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
    update_2: {
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
                "application/json": components["schemas"]["MobilityRestrictionInput"];
            };
        };
        responses: {
            /** @description Restrição editada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MobilityRestriction"];
                };
            };
            /** @description Região fora da lista ou descrição fora do tamanho; o campo vem em errors. */
            400: {
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
            /** @description Sem vínculo ativo com o aluno, ou a restrição não existe ou é de outro. */
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
    remove: {
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
            /** @description Restrição removida. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Sem vínculo ativo com o aluno, ou a restrição não existe ou é de outro. */
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
    update_1: {
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
    activePlans: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Fichas ativas (a lista pode ser vazia). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ActivePlan"][];
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
    versions_1: {
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
                    "application/json": components["schemas"]["WorkoutPlanVersionSummary"][];
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
            /** @description A ficha não existe, é de outro aluno ou ainda é rascunho. */
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
    version_1: {
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
            /** @description Versão. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlanVersion"];
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
            /** @description A ficha ou a versão não existe, é de outro aluno ou não foi publicada. */
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
    mine: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Restrições, da mais antiga para a mais nova. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MobilityRestriction"][];
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
    viewSession: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Treino. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionView"];
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
            /** @description A sessão não existe ou é de outro aluno. */
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
    start: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WorkoutSessionInput"];
            };
        };
        responses: {
            /** @description Sessão iniciada (ou a mesma, se já existia). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutSession"];
                };
            };
            /** @description Ficha não informada. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
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
            /** @description A ficha não existe, não é do aluno ou não está publicada. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Já há um treino em andamento nesta ficha. */
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
    recordFeedback: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SessionFeedback"];
            };
        };
        responses: {
            /** @description Treino com o comentário e a sensação. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionView"];
                };
            };
            /** @description Comentário longo demais ou sensação fora de 1 a 5; o campo vem em errors. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A sessão não existe ou é de outro aluno. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O prazo de correção acabou. */
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
    finish: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Sessão finalizada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutSession"];
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
            /** @description A sessão não existe ou é de outro aluno. */
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
    recordSet: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
                setId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RecordedSetInput"];
            };
        };
        responses: {
            /** @description Série registrada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RecordedSet"];
                };
            };
            /** @description Campo fora das regras ou exercício que não está no treino; o campo vem em errors. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
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
            /** @description A sessão não existe ou é de outro aluno. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O prazo de correção acabou, ou a série já foi registrada com outro id. */
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
    removeSet: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
                setId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Série apagada. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description A sessão ou a série não existe, ou é de outro aluno. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O prazo de correção acabou. */
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
    expiring: {
        parameters: {
            query?: {
                /** @description Dias à frente, de 0 a 60. */
                withinDays?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Fichas vencendo. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExpiringPlan"][];
                };
            };
            /** @description Período fora de 0 a 60 dias. */
            400: {
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
    view: {
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
            /** @description Ficha. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlan"];
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
            /** @description A ficha não existe ou é de outro personal. */
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
                planId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WorkoutPlanInput"];
            };
        };
        responses: {
            /** @description Ficha editada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlan"];
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
            /** @description A conta não é personal. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A ficha não existe, é de outro personal ou o vínculo acabou. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A ficha foi substituída, ou outra edição chegou antes (versão base desatualizada). */
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
    publish: {
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
            /** @description Ficha publicada. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlan"];
                };
            };
            /** @description Alguma divisão está sem exercício (BR-10); a divisão vem em errors. */
            400: {
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
            /** @description A ficha não existe, é de outro personal ou o vínculo acabou. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A ficha já foi publicada. */
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
    versions: {
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
            /** @description Versões. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlanVersionSummary"][];
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
            /** @description A ficha não existe ou é de outro personal. */
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
    version: {
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
            /** @description Versão. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkoutPlanVersion"];
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
            /** @description A ficha ou a versão não existe, ou a ficha é de outro personal. */
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
