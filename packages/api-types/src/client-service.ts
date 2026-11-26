// Gerado por scripts/generate.mjs a partir de openapi/client-service.json (DEV-0022). Não edite à mão.

export interface paths {
    "/clients/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Exclui a conta do cliente e todos os dados dele, sem volta
         * @description Apaga perfil, saúde, avaliações, fotos, vínculos e consentimentos e publica o ContaExcluida para os outros serviços apagarem o que têm. As prescrições ficam com o profissional, sem identificar o cliente (ADR-012). Repetir responde 204.
         */
        delete: operations["deleteAccount"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Histórico de avaliações do cliente autenticado */
        get: operations["mine_5"];
        put?: never;
        /** Registra uma avaliação online (autoaferida) do cliente autenticado */
        post: operations["recordOnline"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments/series": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** A série histórica da evolução corporal do cliente (todo o histórico, ou de from a to) */
        get: operations["getMyBodySeries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments/{assessmentId}/photos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Fotos de uma avaliação do cliente autenticado */
        get: operations["mine_7"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments/{assessmentId}/photos/{pose}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Exclui a foto da pose; o arquivo sai do bucket */
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments/{assessmentId}/photos/{pose}/confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Confirma o envio da foto da pose (substitui a anterior) */
        post: operations["confirm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments/{assessmentId}/photos/{pose}/upload-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** URL pré-assinada para enviar a foto da pose (5 minutos) */
        post: operations["uploadUrl"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/assessments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Exclui uma avaliação do cliente autenticado */
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/clients/me/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Exporta todos os dados que o client-service tem do cliente
         * @description Perfil, saúde, avaliações com as fotos (URL temporária de 5 minutos), vínculos com o consentimento de cada um e as leituras da saúde pelos profissionais, sem o conteúdo. O app junta com a exportação do training e do nutrition.
         */
        get: operations["export"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
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
    "/invitations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Convites do profissional autenticado, do mais novo para o mais antigo */
        get: operations["mine_4"];
        put?: never;
        /** Cria um convite (por e-mail ou para compartilhar) */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/invitations/lookup": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Consulta um convite pelo token do link ou pelo código, para a tela de aceite */
        post: operations["lookup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/invitations/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Cancela um convite do profissional autenticado */
        delete: operations["cancel"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/links": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Vínculos do cliente autenticado, ativos e encerrados */
        get: operations["mine_3"];
        put?: never;
        /** Aceita um convite (ou o código pessoal do profissional) com consentimento */
        post: operations["accept"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/links/{id}/end": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Encerra o vínculo (pelo cliente ou pelo profissional que é parte dele) */
        post: operations["end"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/clients": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Clientes com vínculo ativo do profissional autenticado */
        get: operations["mine_6"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/clients/{clientId}/assessments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Histórico de avaliações de um cliente com vínculo ativo */
        get: operations["forClient"];
        put?: never;
        /** Registra uma avaliação presencial de um cliente com vínculo ativo */
        post: operations["recordPresential"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/clients/{clientId}/assessments/series": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** A série histórica da evolução corporal de um cliente com vínculo ativo */
        get: operations["getClientBodySeries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/clients/{clientId}/assessments/{assessmentId}/photos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Fotos de uma avaliação de cliente com vínculo ativo */
        get: operations["forClient_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/clients/{clientId}/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Perfil de saúde de um cliente com vínculo ativo (cada leitura fica registrada) */
        get: operations["health"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/clients/{clientId}/profile": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Perfil de um cliente com vínculo ativo */
        get: operations["profile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/professionals/me/invite-code": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Código pessoal do profissional autenticado */
        get: operations["mine_2"];
        put?: never;
        /** Troca o código pessoal; o antigo deixa de valer */
        post: operations["rotate"];
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
        /** @description Informe o token do link ou o código. */
        AcceptInvitationRequest: {
            /** @description Código do convite ou código pessoal do profissional. */
            code?: string | null;
            /** @description Consentimento explícito; precisa ser true. */
            consent?: boolean;
            /**
             * @description Versão do termo que a tela mostrou.
             * @example v1
             */
            termsVersion?: string;
            token?: string | null;
        };
        Assessment: {
            /** Format: uuid */
            authorId?: string;
            /** @enum {string} */
            authorRole?: "CLIENT" | "PROFESSIONAL";
            /** @description % de gordura de cada origem; vazio quando não há dados para calcular. */
            bodyFat?: components["schemas"]["BodyFatEstimate"][];
            circumferences?: components["schemas"]["Circumferences"];
            /** Format: date-time */
            createdAt?: string;
            /** Format: uuid */
            id?: string;
            /** Format: date */
            measuredOn?: string;
            /** @enum {string|null} */
            sexForFormulas?: "FEMALE" | "MALE" | null;
            skinfolds?: components["schemas"]["Skinfolds"];
            /** @enum {string} */
            type?: "PRESENTIAL" | "ONLINE";
            weightKg?: number;
        };
        AssessmentRequest: {
            /** @description % de gordura de uma bioimpedância, de 3 a 70. */
            bioimpedanceBodyFatPercent?: number | null;
            circumferences?: components["schemas"]["Circumferences"];
            /** Format: date */
            measuredOn?: string;
            /**
             * @description Sexo usado só nos cálculos de % de gordura.
             * @enum {string|null}
             */
            sexForFormulas?: "FEMALE" | "MALE" | null;
            skinfolds?: components["schemas"]["Skinfolds"];
            /** @description Peso em kg, de 20 a 400. */
            weightKg?: number;
        };
        BodyFatEstimate: {
            /** @enum {string} */
            origin?: "POLLOCK_3" | "POLLOCK_7" | "NAVY" | "BIOIMPEDANCE";
            percent?: number;
        };
        /** @description A série do % de gordura de um método; métodos não se misturam (BR-12). */
        BodyFatSeries: {
            /** @enum {string} */
            origin?: "POLLOCK_3" | "POLLOCK_7" | "NAVY" | "BIOIMPEDANCE";
            points?: components["schemas"]["BodySeriesPoint"][];
        };
        /** @description A série histórica de peso, circunferências e % de gordura por método. */
        BodySeries: {
            /** @description Uma série por método de % de gordura, sem misturar métodos. */
            bodyFat?: components["schemas"]["BodyFatSeries"][];
            /** @description Uma série por circunferência medida (neck, chest, waist, abdomen, hip, arm, forearm, thigh, calf), em cm. */
            circumferences?: {
                [key: string]: components["schemas"]["BodySeriesPoint"][];
            };
            weightKg?: components["schemas"]["BodySeriesPoint"][];
        };
        /** @description Um valor numa data de medição. */
        BodySeriesPoint: {
            /** Format: date */
            date?: string;
            value?: number;
        };
        /** @description Circunferências em cm, todas opcionais. */
        Circumferences: {
            abdomen?: number | null;
            arm?: number | null;
            calf?: number | null;
            chest?: number | null;
            forearm?: number | null;
            hip?: number | null;
            neck?: number | null;
            thigh?: number | null;
            waist?: number | null;
        };
        /** @description Todos os dados que o client-service tem do cliente. */
        ClientExport: {
            /** @description Todas as leituras de dados sensíveis pelos profissionais (saúde, avaliações e fotos). */
            accessLog?: components["schemas"]["ClientExportAccess"][];
            assessments?: components["schemas"]["ClientExportAssessment"][];
            /** Format: date-time */
            exportedAt?: string;
            /** @description Ausente se o cliente nunca preencheu o perfil de saúde. */
            health?: components["schemas"]["HealthProfile"];
            /** @description Só as leituras da saúde; o registro completo está em accessLog. */
            healthReads?: components["schemas"]["ClientExportHealthRead"][];
            links?: components["schemas"]["ClientExportLink"][];
            /** @description Ausente se a conta ainda não tem perfil. */
            profile?: components["schemas"]["ClientProfile"];
        };
        /** @description Leitura de dado sensível pelo profissional, sem o conteúdo. */
        ClientExportAccess: {
            /** Format: date-time */
            accessedAt?: string;
            /** Format: uuid */
            professionalId?: string;
            professionalName?: string;
            /** @enum {string} */
            resource?: "HEALTH" | "ASSESSMENTS" | "PHOTOS";
        };
        /** @description Avaliação com as fotos, cada uma com a URL temporária. */
        ClientExportAssessment: {
            assessment?: components["schemas"]["Assessment"];
            photos?: components["schemas"]["Photo"][];
        };
        /** @description Consentimento dado no aceite do vínculo. */
        ClientExportConsent: {
            /** Format: date-time */
            grantedAt?: string;
            scope?: ("PROFILE" | "HEALTH" | "ASSESSMENTS" | "PHOTOS")[];
            termsVersion?: string;
        };
        /** @description Leitura do perfil de saúde pelo profissional, sem o conteúdo. */
        ClientExportHealthRead: {
            /** Format: date-time */
            accessedAt?: string;
            /** Format: uuid */
            professionalId?: string;
            professionalName?: string;
        };
        /** @description Vínculo, ativo ou encerrado, com o consentimento. */
        ClientExportLink: {
            consent?: components["schemas"]["ClientExportConsent"];
            /** Format: date-time */
            endedAt?: string;
            /** @enum {string} */
            endedBy?: "CLIENT" | "PROFESSIONAL";
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            professionalId?: string;
            professionalName?: string;
            /** @enum {string} */
            professionalType?: "PERSONAL_TRAINER" | "NUTRITIONIST";
            /** Format: date-time */
            startedAt?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "ENDED";
        };
        /** @description Vínculo visto pelo cliente. */
        ClientLink: {
            /** Format: date-time */
            endedAt?: string | null;
            /** @enum {string|null} */
            endedBy?: "CLIENT" | "PROFESSIONAL" | null;
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            professionalId?: string;
            professionalName?: string;
            /** @enum {string} */
            professionalType?: "PERSONAL_TRAINER" | "NUTRITIONIST";
            /** Format: date-time */
            startedAt?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "ENDED";
        };
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
        CreatedInvitation: {
            /** @description Código de 8 caracteres; só aparece nesta resposta. */
            code?: string;
            email?: string | null;
            /** @description Se o e-mail saiu; false sem e-mail ou se o envio falhou. */
            emailSent?: boolean;
            /** Format: date-time */
            expiresAt?: string;
            /** Format: uuid */
            id?: string;
            /** @description Link do convite; só aparece nesta resposta. */
            link?: string;
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
        Invitation: {
            /** Format: date-time */
            createdAt?: string;
            email?: string | null;
            /** Format: date-time */
            expiresAt?: string;
            /** Format: uuid */
            id?: string;
            /** @enum {string} */
            state?: "PENDING" | "EXPIRED" | "ACCEPTED" | "CANCELLED";
        };
        /** @description Informe o token do link ou o código. */
        InvitationLookupRequest: {
            code?: string | null;
            token?: string | null;
        };
        InvitationPreview: {
            /**
             * Format: date-time
             * @description Vazio para o código pessoal do profissional, que não vence.
             */
            expiresAt?: string | null;
            /** @enum {string} */
            kind?: "INVITATION" | "PROFESSIONAL_CODE";
            professionalName?: string;
            /** @enum {string} */
            professionalType?: "PERSONAL_TRAINER" | "NUTRITIONIST";
            sharedData?: ("PROFILE" | "HEALTH" | "ASSESSMENTS" | "PHOTOS")[];
            /** @description Texto do termo de consentimento. */
            termsText?: string;
            /** @description Versão do termo mostrado; o aceite repete esta versão. */
            termsVersion?: string;
        };
        InvitationRequest: {
            /**
             * Format: email
             * @description E-mail do cliente; sem ele, o convite volta para o profissional compartilhar.
             */
            email?: string;
        };
        InviteCode: {
            /** @description Código pessoal de 8 caracteres. */
            code?: string;
        };
        Link: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            professionalId?: string;
            professionalName?: string;
            /** @enum {string} */
            professionalType?: "PERSONAL_TRAINER" | "NUTRITIONIST";
            sharedData?: ("PROFILE" | "HEALTH" | "ASSESSMENTS" | "PHOTOS")[];
            /** Format: date-time */
            startedAt?: string;
        };
        /** @description Situação do vínculo depois do encerramento. */
        LinkStatus: {
            /** Format: date-time */
            endedAt?: string | null;
            /** @enum {string|null} */
            endedBy?: "CLIENT" | "PROFESSIONAL" | null;
            /** Format: uuid */
            id?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "ENDED";
        };
        Photo: {
            /** Format: date-time */
            expiresAt?: string;
            /** @enum {string} */
            pose?: "FRONT" | "SIDE" | "BACK";
            /** Format: date-time */
            uploadedAt?: string;
            /** Format: uri */
            url?: string;
        };
        PhotoConfirmRequest: {
            uploadId?: string;
        };
        PhotoUpload: {
            /** Format: date-time */
            expiresAt?: string;
            /** @description Repetido na confirmação. */
            uploadId?: string;
            /**
             * Format: uri
             * @description PUT do arquivo, com o mesmo Content-Type.
             */
            uploadUrl?: string;
        };
        PhotoUploadRequest: {
            /** @description image/jpeg ou image/png. */
            contentType?: string;
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
        ProfessionalClient: {
            /** Format: uuid */
            clientId?: string;
            clientName?: string;
            /** Format: uuid */
            linkId?: string;
            /** Format: date-time */
            startedAt?: string;
        };
        /** @description Dobras cutâneas em mm, só na avaliação presencial. */
        Skinfolds: {
            abdomen?: number | null;
            chest?: number | null;
            midaxillary?: number | null;
            subscapular?: number | null;
            suprailiac?: number | null;
            thigh?: number | null;
            triceps?: number | null;
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
    deleteAccount: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Conta excluída. */
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
            /** @description A conta não tem o papel de cliente. */
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
    mine_5: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Da medição mais recente para a mais antiga. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Assessment"][];
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
    recordOnline: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssessmentRequest"];
            };
        };
        responses: {
            /** @description Avaliação registrada. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Assessment"];
                };
            };
            /** @description Campo inválido. */
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
    getMyBodySeries: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Peso, circunferências e % de gordura por método. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BodySeries"];
                };
            };
            /** @description Período invertido; o campo vem em errors. */
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
    mine_7: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assessmentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Fotos com URLs temporárias de leitura. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Photo"][];
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
            /** @description Avaliação não encontrada (ou de outra pessoa). */
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
    delete_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assessmentId: string;
                pose: "FRONT" | "SIDE" | "BACK";
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Foto excluída (ou não havia). */
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
            /** @description Avaliação não encontrada (ou de outra pessoa). */
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
    confirm: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assessmentId: string;
                pose: "FRONT" | "SIDE" | "BACK";
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PhotoConfirmRequest"];
            };
        };
        responses: {
            /** @description Foto confirmada, com a URL temporária de leitura. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Photo"];
                };
            };
            /** @description Arquivo ausente, de outro tipo ou acima de 10 MB. */
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
            /** @description Avaliação não encontrada (ou de outra pessoa). */
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
    uploadUrl: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assessmentId: string;
                pose: "FRONT" | "SIDE" | "BACK";
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PhotoUploadRequest"];
            };
        };
        responses: {
            /** @description URL e id do envio. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PhotoUpload"];
                };
            };
            /** @description Tipo de arquivo não aceito. */
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
            /** @description Avaliação não encontrada (ou de outra pessoa). */
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
    delete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Avaliação excluída. */
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
            /** @description Avaliação não encontrada (ou de outra pessoa). */
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
    export: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Os dados do cliente. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientExport"];
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
            /** @description A conta não tem o papel de cliente. */
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
    mine_4: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Convites do profissional. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Invitation"][];
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O cadastro de profissional ainda está sendo processado. */
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
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["InvitationRequest"];
            };
        };
        responses: {
            /** @description Convite criado. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreatedInvitation"];
                };
            };
            /** @description E-mail inválido. */
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O cadastro de profissional ainda está sendo processado. */
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
    lookup: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["InvitationLookupRequest"];
            };
        };
        responses: {
            /** @description Quem convida e o que será compartilhado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InvitationPreview"];
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
            /** @description Convite ou código não encontrado. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Convite vencido, já usado ou cancelado. */
            410: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    cancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Convite cancelado (ou já estava). */
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Convite não encontrado (ou de outro profissional). */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Convite já aceito. */
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
    mine_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Vínculos do cliente, do mais novo para o mais antigo. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientLink"][];
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
    accept: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AcceptInvitationRequest"];
            };
        };
        responses: {
            /** @description Vínculo criado e consentimento registrado. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Link"];
                };
            };
            /** @description Sem o consentimento explícito. */
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
            /** @description Convite ou código não encontrado. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Já há vínculo ativo do mesmo tipo, vínculo consigo mesmo, termo desatualizado ou perfil ainda não criado. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Convite vencido, já usado ou cancelado. */
            410: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
    end: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Vínculo encerrado (ou já estava; vale o primeiro encerramento). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LinkStatus"];
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
            /** @description Vínculo não encontrado, ou a conta não é parte dele. */
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
    mine_6: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Vínculos ativos, do mais novo para o mais antigo. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProfessionalClient"][];
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
            /** @description A conta não é profissional. */
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
    forClient: {
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
            /** @description Da medição mais recente para a mais antiga. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Assessment"][];
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo com o cliente. */
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
    recordPresential: {
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
                "application/json": components["schemas"]["AssessmentRequest"];
            };
        };
        responses: {
            /** @description Avaliação registrada. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Assessment"];
                };
            };
            /** @description Campo inválido. */
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo com o cliente. */
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
    getClientBodySeries: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path: {
                clientId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Peso, circunferências e % de gordura por método. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BodySeries"];
                };
            };
            /** @description Período invertido; o campo vem em errors. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo com o cliente. */
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
    forClient_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                clientId: string;
                assessmentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Fotos com URLs temporárias de leitura. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Photo"][];
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo, ou avaliação de outro cliente. */
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
    health: {
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
            /** @description Perfil de saúde do cliente. */
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description Sem vínculo ativo, ou o cliente ainda não informou a saúde. */
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
    profile: {
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
            /** @description Perfil do cliente. */
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
            /** @description A conta não é profissional. */
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
    mine_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Código pessoal. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InviteCode"];
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O cadastro de profissional ainda está sendo processado. */
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
    rotate: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Código novo. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InviteCode"];
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
            /** @description A conta não é profissional. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description O cadastro de profissional ainda está sendo processado. */
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
}
