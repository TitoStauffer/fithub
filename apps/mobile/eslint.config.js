// ESLint do app mobile (DEV-0021): a configuração oficial do Expo (React, React Native, hooks e TypeScript).
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([expoConfig, { ignores: ['reports/*', '.expo/*', 'dist/*'] }]);
