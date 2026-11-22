// Gera os tipos TypeScript a partir dos contratos OpenAPI versionados (DEV-0022).
//
//   pnpm --filter @fithub/api-types generate
//
// Lê openapi/<servico>.json (gravados pelo teste de esqueleto de cada serviço) e escreve
// src/<servico>.ts e src/index.ts. O teste deste pacote falha se os arquivos commitados não baterem
// com o que esta geração produz.
import { mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { basename, join } from 'node:path';

import openapiTS, { astToString } from 'openapi-typescript';

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const HEADER = '// Gerado por scripts/generate.mjs a partir de openapi/%s.json (DEV-0022). Não edite à mão.\n\n';

/** Serviços com contrato: os arquivos openapi/*.json, em ordem alfabética. */
export function services(root = ROOT) {
    return readdirSync(join(root, 'openapi'))
        .filter((file) => file.endsWith('.json'))
        .map((file) => basename(file, '.json'))
        .sort();
}

/** Nome do tipo exportado no index: account-service -> AccountService. */
export function typeName(service) {
    return service.replace(/(^|-)([a-z0-9])/g, (_, __, letter) => letter.toUpperCase());
}

/** Conteúdo de cada arquivo gerado, por caminho relativo à raiz do pacote. */
export async function generate(root = ROOT) {
    const files = new Map();
    const names = services(root);
    for (const service of names) {
        if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(service)) throw new Error(`Nome de contrato inválido: ${service}`);
        const ast = await openapiTS(pathToFileURL(join(root, 'openapi', `${service}.json`)));
        files.set(`src/${service}.ts`, HEADER.replace('%s', service) + astToString(ast));
    }
    const index = names
        .map((service) => {
            const name = typeName(service);
            return `export type {\n    paths as ${name}Paths,\n    components as ${name}Components,\n    operations as ${name}Operations,\n} from './${service}';\n`;
        })
        .join('');
    files.set('src/index.ts', HEADER.replace('%s', '*') + index);
    return files;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    const files = await generate();
    mkdirSync(join(ROOT, 'src'), { recursive: true });
    for (const [path, content] of files) {
        writeFileSync(join(ROOT, path), content);
        console.log(`gerado: ${path}`);
    }
}
