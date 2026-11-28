// @vitest-environment node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const css = readFileSync(fileURLToPath(new URL('./index.css', import.meta.url)), 'utf8');

/**
 * Luminância relativa de um token oklch cinza (croma 0): sem croma, os três canais lineares do sRGB valem L³, e a
 * luminância também. Token com croma precisa de outra conta, então o teste recusa.
 */
function luminance(token: string): number {
    const match = new RegExp(`--${token}:\\s*oklch\\(([0-9.]+) 0 0\\);`).exec(css);
    if (!match) throw new Error(`--${token} não é um oklch cinza`);
    return Number(match[1]) ** 3;
}

const contrast = (first: number, second: number) =>
    (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);

describe('tokens do tema da web', () => {
    // WCAG 2.2, 1.4.11 (DEV-0144): o indicador de foco precisa de 3:1 sobre o fundo ao lado dele.
    it('o anel de foco tem contraste de pelo menos 3:1 sobre o fundo', () => {
        expect(contrast(luminance('ring'), luminance('background'))).toBeGreaterThanOrEqual(3);
    });
});
