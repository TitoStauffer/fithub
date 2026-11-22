/// <reference types="vite/client" />

interface ImportMetaEnv {
    /** URL base do account-service; sem ela, http://localhost:8081 (porta local, ENGENHARIA.md §5). */
    readonly VITE_ACCOUNT_API_URL?: string;
    /** URL do Keycloak; sem ela, http://localhost:8080 (compose local, infra/README.md). */
    readonly VITE_KEYCLOAK_URL?: string;
}
