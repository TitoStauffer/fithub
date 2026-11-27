/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import { devApiProxy } from './src/devProxy';

// Vite e Vitest do app web (DEV-0020). O alias "@" aponta para src, como no components.json do shadcn/ui.
export default defineConfig({
    plugins: [react(), tailwindcss()],
    // No `pnpm dev`, as APIs ficam na origem da página pelo proxy (DEV-0156); os caminhos vêm do .env.development.
    server: { proxy: devApiProxy() },
    resolve: {
        alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
        // O JUnit alimenta a guarda de teste pulado do CI (tools/repo-checks/check-skipped-tests.mjs).
        reporters: ['default', ['junit', { outputFile: 'reports/junit.xml' }]],
    },
});
