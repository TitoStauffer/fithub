// ESLint do app web (DEV-0020): regras recomendadas de JS e TypeScript (com tipos), hooks do React e
// Fast Refresh. A formatação fica com o Prettier ("pnpm lint" roda os dois).
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    { ignores: ['dist', 'reports', 'coverage'] },
    {
        files: ['**/*.{ts,tsx,js}'],
        extends: [js.configs.recommended, ...tseslint.configs.recommendedTypeChecked],
        languageOptions: {
            globals: globals.browser,
            parserOptions: {
                // O próprio eslint.config.js fica fora do tsconfig (não é código do app).
                projectService: { allowDefaultProject: ['eslint.config.js'] },
                tsconfigRootDir: import.meta.dirname,
            },
        },
        plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
        rules: {
            ...reactHooks.configs.recommended.rules,
            'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
        },
    },
    {
        // Componentes do shadcn/ui exportam o componente e as variantes juntos, como o gerador entrega.
        files: ['src/components/ui/**/*.tsx'],
        rules: { 'react-refresh/only-export-components': 'off' },
    },
);
