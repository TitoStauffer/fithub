import { useState, useEffect } from 'react'

export default function ProgressChart({ clientId }) {
  const [data, setData] = useState([
    { date: '2024-07-01', weight: 80, imc: 24.2 },
    { date: '2024-07-08', weight: 79.5, imc: 24.0 },
    { date: '2024-07-15', weight: 79, imc: 23.8 },
    { date: '2024-07-22', weight: 78, imc: 23.5 },
    { date: '2024-07-29', weight: 77, imc: 23.2 },
    { date: '2024-08-05', weight: 76, imc: 23.0 },
    { date: '2024-08-12', weight: 75, imc: 22.7 }
  ])

  const weightChange = data[data.length - 1].weight - data[0].weight
  const imcChange = data[data.length - 1].imc - data[0].imc

  return (
    <div className="card">
      <h2>Progresso do Cliente</h2>

      <div className="progress-summary">
        <div className="progress-stat">
          <h4>Mudança de Peso</h4>
          <p className={`value ${weightChange < 0 ? 'positive' : 'negative'}`}>
            {weightChange < 0 ? '↓' : '↑'} {Math.abs(weightChange).toFixed(1)} kg
          </p>
          <span className="period">Últimos 30 dias</span>
        </div>

        <div className="progress-stat">
          <h4>Mudança de IMC</h4>
          <p className={`value ${imcChange < 0 ? 'positive' : 'negative'}`}>
            {imcChange < 0 ? '↓' : '↑'} {Math.abs(imcChange).toFixed(2)}
          </p>
          <span className="period">Últimos 30 dias</span>
        </div>
      </div>

      <div className="chart-container">
        <div className="mini-chart">
          <h4>Evolução de Peso (kg)</h4>
          <svg viewBox="0 0 700 200" xmlns="http://www.w3.org/2000/svg">
            {/* Simple line chart */}
            <polyline
              points={data.map((d, i) => `${i * 100},${200 - (d.weight - 74) * 30}`).join(' ')}
              fill="none"
              stroke="#2563eb"
              strokeWidth="2"
            />
            {/* Grid lines */}
            {[75, 76, 77, 78, 79, 80].map((weight, i) => (
              <line
                key={`grid-${i}`}
                x1="0"
                y1={200 - (weight - 74) * 30}
                x2="700"
                y2={200 - (weight - 74) * 30}
                stroke="#e5e7eb"
                strokeDasharray="5,5"
              />
            ))}
            {/* Y-axis labels */}
            {[75, 76, 77, 78, 79, 80].map((weight) => (
              <text
                key={`label-${weight}`}
                x="-5"
                y={200 - (weight - 74) * 30 + 5}
                textAnchor="end"
                fontSize="12"
                fill="#6b7280"
              >
                {weight}
              </text>
            ))}
            {/* Data points */}
            {data.map((d, i) => (
              <circle
                key={`point-${i}`}
                cx={i * 100}
                cy={200 - (d.weight - 74) * 30}
                r="4"
                fill="#2563eb"
              />
            ))}
          </svg>
        </div>
      </div>

      <div className="progress-table">
        <h4>Histórico Detalhado</h4>
        <table>
          <thead>
            <tr>
              <th>Data</th>
              <th>Peso</th>
              <th>IMC</th>
              <th>Mudança</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d, i) => (
              <tr key={i}>
                <td>{new Date(d.date).toLocaleDateString('pt-BR')}</td>
                <td>{d.weight} kg</td>
                <td>{d.imc}</td>
                <td>
                  {i > 0 ? (
                    <span className={data[i].weight < data[i - 1].weight ? 'positive' : 'negative'}>
                      {(data[i].weight - data[i - 1].weight).toFixed(1)} kg
                    </span>
                  ) : (
                    '-'
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
