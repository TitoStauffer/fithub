import type { ClientServicePaths } from '@fithub/api-types';

/**
 * Rotas do client-service, tipadas a partir do OpenAPI (packages/api-types, DEV-0022). O cliente HTTP
 * tipado chega com a primeira rota de negócio; por enquanto o contrato ainda não tem rotas.
 */
export type ClientApi = ClientServicePaths;
