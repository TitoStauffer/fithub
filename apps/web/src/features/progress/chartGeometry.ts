/** Um ponto do gráfico: o rótulo do eixo (a data) e o valor. */
export interface ChartPoint {
    label: string;
    value: number;
}

export const WIDTH = 320;
export const HEIGHT = 120;
const PAD = 8;

/** As coordenadas da linha, com o menor valor embaixo e o maior em cima; um ponto só fica no meio. */
export function chartCoordinates(points: ChartPoint[]): { x: number; y: number }[] {
    const values = points.map((point) => point.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min;
    return points.map((point, index) => ({
        x: points.length === 1 ? WIDTH / 2 : PAD + (index * (WIDTH - 2 * PAD)) / (points.length - 1),
        y: span === 0 ? HEIGHT / 2 : PAD + ((max - point.value) * (HEIGHT - 2 * PAD)) / span,
    }));
}
