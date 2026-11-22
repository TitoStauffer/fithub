import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { generate, services, typeName } from './generate.mjs';

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const REPOSITORY = join(ROOT, '..', '..');

test('os tipos commitados batem com a geração a partir dos contratos OpenAPI', async () => {
    const files = await generate();
    for (const [path, expected] of files) {
        const file = join(ROOT, path);
        assert.ok(existsSync(file), `${path} não existe: rode pnpm --filter @fithub/api-types generate`);
        // Checkout no Windows pode trazer CRLF (core.autocrlf); o conteúdo é o mesmo.
        const committed = readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
        assert.equal(
            committed,
            expected,
            `${path} desatualizado: rode pnpm --filter @fithub/api-types generate`,
        );
    }
    // Nenhum arquivo gerado sobrando de um contrato que saiu.
    const generated = readdirSync(join(ROOT, 'src'))
        .map((file) => `src/${file}`)
        .sort();
    assert.deepEqual(generated, [...files.keys()].sort());
});

test('todo serviço com Dockerfile tem contrato OpenAPI', () => {
    const withImage = readdirSync(join(REPOSITORY, 'services'), { withFileTypes: true })
        .filter(
            (entry) =>
                entry.isDirectory() && existsSync(join(REPOSITORY, 'services', entry.name, 'Dockerfile')),
        )
        .map((entry) => entry.name)
        .sort();
    assert.deepEqual(services(), withImage);
});

test('o index exporta paths, components e operations de cada serviço', async () => {
    const index = (await generate()).get('src/index.ts');
    for (const service of services()) {
        const name = typeName(service);
        for (const kind of ['Paths', 'Components', 'Operations']) {
            assert.match(index, new RegExp(`as ${name}${kind},`), `${name}${kind}`);
        }
    }
    assert.equal(typeName('account-service'), 'AccountService');
});
