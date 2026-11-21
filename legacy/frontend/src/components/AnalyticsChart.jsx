import { useState, useEffect } from 'react'
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell
} from 'recharts'
import { apiClient } from '../services/api'
import './AnalyticsChart.css'

export default function AnalyticsChart({ clientId }) {
  const [biometricData, setBiometricData] = useState([])
  const [workoutStats, setWorkoutStats] = useState({})
  const [mealStats, setMealStats] = useState({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadAnalytics()
  }, [clientId])

  async function loadAnalytics() {
    try {
      // Buscar histórico de biometria (simulado com dados de 30 dias)
      const biometricHistory = [
        { date: '01 Ago', weight: 80, imc: 24.2 },
        { date: '05 Ago', weight: 79.5, imc: 24.0 },
        { date: '10 Ago', weight: 79, imc: 23.8 },
        { date: '15 Ago', weight: 78, imc: 23.5 },
        { date: '20 Ago', weight: 77, imc: 23.2 },
        { date: '25 Ago', weight: 76, imc: 23.0 },
        { date: '30 Ago', weight: 75, imc: 22.7 }
      ]
      setBiometricData(biometricHistory)

      // Buscar execuções de treino
      const workoutsResponse = await apiClient.get(`/patients/${clientId}/training/executions`)
      const workouts = workoutsResponse.data

      const completedCount = workouts.filter(w => w.status === 'COMPLETED').length
      const partialCount = workouts.filter(w => w.status === 'PARTIAL').length
      const skippedCount = workouts.filter(w => w.status === 'SKIPPED').length

      setWorkoutStats({
        total: workouts.length,
        completed: completedCount,
        partial: partialCount,
        skipped: skippedCount,
        completionRate: workouts.length > 0 ? Math.round((completedCount / workouts.length) * 100) : 0
      })

      // Buscar execuções de refeição
      const mealsResponse = await apiClient.get(`/patients/${clientId}/nutrition/executions`)
      const meals = mealsResponse.data

      const completedMeals = meals.filter(m => m.completed).length
      const avgCalories = meals.length > 0
        ? Math.round(meals.reduce((sum, m) => sum + m.calories, 0) / meals.length)
        : 0

      setMealStats({
        total: meals.length,
        completed: completedMeals,
        averageCalories: avgCalories,
        completionRate: meals.length > 0 ? Math.round((completedMeals / meals.length) * 100) : 0
      })
    } catch (error) {
      console.error('Error loading analytics:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Carregando análises...</div>
  }

  const workoutStatusData = [
    { name: 'Completado', value: workoutStats.completed, fill: '#10b981' },
    { name: 'Parcial', value: workoutStats.partial, fill: '#f59e0b' },
    { name: 'Não fez', value: workoutStats.skipped, fill: '#ef4444' }
  ]

  const mealStatusData = [
    { name: 'Completado', value: mealStats.completed, fill: '#10b981' },
    { name: 'Não completado', value: mealStats.total - mealStats.completed, fill: '#ef4444' }
  ]

  return (
    <div className="analytics-container">
      <div className="analytics-grid">
        {/* Gráfico de evolução de peso */}
        <div className="chart-card">
          <h3>Evolução de Peso - 30 Dias</h3>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={biometricData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis domain={['dataMin - 2', 'dataMax + 2']} />
              <Tooltip formatter={(value) => value.toFixed(1)} />
              <Legend />
              <Line
                type="monotone"
                dataKey="weight"
                stroke="#2563eb"
                name="Peso (kg)"
                dot={{ fill: '#2563eb' }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Gráfico de status de treinos */}
        <div className="chart-card">
          <h3>Status de Treinos</h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={workoutStatusData}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, value }) => `${name}: ${value}`}
                outerRadius={100}
                fill="#8884d8"
                dataKey="value"
              >
                {workoutStatusData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.fill} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* Gráfico de status de refeições */}
        <div className="chart-card">
          <h3>Status de Refeições</h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={mealStatusData}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, value }) => `${name}: ${value}`}
                outerRadius={100}
                fill="#8884d8"
                dataKey="value"
              >
                {mealStatusData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.fill} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* Estatísticas resumidas */}
        <div className="stats-summary">
          <h3>Resumo de Atividades</h3>

          <div className="stat-item">
            <span className="stat-label">Total de Treinos</span>
            <span className="stat-value">{workoutStats.total}</span>
            <span className="stat-subtitle">Taxa de conclusão: {workoutStats.completionRate}%</span>
          </div>

          <div className="stat-item">
            <span className="stat-label">Total de Refeições</span>
            <span className="stat-value">{mealStats.total}</span>
            <span className="stat-subtitle">Média: {mealStats.averageCalories} kcal</span>
          </div>

          <div className="stat-item">
            <span className="stat-label">Taxa de Completude</span>
            <div className="progress-bar">
              <div className="progress-fill" style={{ width: `${workoutStats.completionRate}%` }}></div>
            </div>
            <span className="stat-subtitle">Treinos: {workoutStats.completionRate}%</span>
          </div>

          <div className="stat-item">
            <span className="stat-label">Taxa de Refeições</span>
            <div className="progress-bar">
              <div className="progress-fill" style={{ width: `${mealStats.completionRate}%` }}></div>
            </div>
            <span className="stat-subtitle">Refeições: {mealStats.completionRate}%</span>
          </div>
        </div>
      </div>
    </div>
  )
}
