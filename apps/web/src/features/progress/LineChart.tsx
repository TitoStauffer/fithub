import { chartCoordinates, HEIGHT, WIDTH, type ChartPoint } from './chartGeometry';

/**
 * Linha simples em SVG, sem biblioteca (DEV-0094). O desenho é decorativo para o leitor de tela: a tabela logo abaixo
 * traz os mesmos valores.
 */
export function LineChart({ title, unit, points }: { title: string; unit: string; points: ChartPoint[] }) {
    const coordinates = chartCoordinates(points);
    return (
        <figure className="space-y-1">
            <svg
                aria-hidden="true"
                viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
                className="h-28 w-full max-w-md rounded border"
            >
                <polyline
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    points={coordinates.map(({ x, y }) => `${x},${y}`).join(' ')}
                />
                {coordinates.map(({ x, y }, index) => (
                    <circle key={index} cx={x} cy={y} r="3" fill="currentColor" />
                ))}
            </svg>
            <table className="text-sm">
                <caption className="text-left font-medium">{title}</caption>
                <thead>
                    <tr>
                        <th scope="col" className="pr-4 text-left">
                            Data
                        </th>
                        <th scope="col" className="text-left">
                            Valor ({unit})
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {points.map((point, index) => (
                        <tr key={index}>
                            <td className="pr-4">{point.label}</td>
                            <td>{point.value.toLocaleString('pt-BR')}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </figure>
    );
}
