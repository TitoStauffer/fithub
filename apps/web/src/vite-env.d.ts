/// <reference types="vite/client" />

interface ImportMetaEnv {
    /** URL base do account-service; sem ela, http://localhost:8081 (porta local, ENGENHARIA.md §5). */
    readonly VITE_ACCOUNT_API_URL?: string;
}
