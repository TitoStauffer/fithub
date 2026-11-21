import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { apiClient } from '../services/api'
import WorkoutExecutionForm from '../components/WorkoutExecutionForm'
import MealExecutionForm from '../components/MealExecutionForm'
import ProgressChart from '../components/ProgressChart'
import AnalyticsChart from '../components/AnalyticsChart'
import './ClientDetail.css'

export default function ClientDetailPage() {
  const { clientId } = useParams()
  const [client, setClient] = useState(null)
  const [activeTab, setActiveTab] = useState('profile')
  const [workoutExecutions, setWorkoutExecutions] = useState([])
  const [mealExecutions, setMealExecutions] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadClientData()
  }, [clientId])

  async function loadClientData() {
    try {
      // Buscar dados do cliente
      const clientResponse = await apiClient.get(`/patients/${clientId}`)
      const patientData = clientResponse.data

      setClient({
        id: clientId,
        name: patientData.name || 'Cliente',
        weight: patientData.weight,
        age: patientData.age,
        height: patientData.height,
        imc: patientData.imc,
        status: patientData.status,
        createdAt: patientData.createdAt
      })

      // Buscar histórico de treinos reais
      try {
        const workoutsResponse = await apiClient.get(`/patients/${clientId}/training/executions`)
        setWorkoutExecutions(workoutsResponse.data)
      } catch (err) {
        console.warn('Could not load workout history:', err)
        setWorkoutExecutions([])
      }

      // Buscar histórico de refeições reais
      try {
        const mealsResponse = await apiClient.get(`/patients/${clientId}/nutrition/executions`)
        setMealExecutions(mealsResponse.data)
      } catch (err) {
        console.warn('Could not load meal history:', err)
        setMealExecutions([])
      }
    } catch (error) {
      console.error('Error loading client:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !client) {
    return <div className="loading">Carregando...</div>
  }

  return (
    <div className="container">
      <div className="client-header">
        <div className="client-info-card">
          <h1>{client.name}</h1>
          <div className="client-metrics">
            <div className="metric">
              <span className="label">Peso</span>
              <span className="value">{client.weight} kg</span>
            </div>
            <div className="metric">
              <span className="label">Idade</span>
              <span className="value">{client.age} anos</span>
            </div>
            <div className="metric">
              <span className="label">Altura</span>
              <span className="value">{client.height} cm</span>
            </div>
            <div className="metric">
              <span className="label">IMC</span>
              <span className="value">{client.imc}</span>
            </div>
            <div className="metric">
              <span className="label">Status</span>
              <span className={`status ${client.status.toLowerCase()}`}>
                {client.status === 'ACTIVE' ? '✅ Ativo' : '⏸️ Inativo'}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="tabs">
        <button
          className={`tab ${activeTab === 'profile' ? 'active' : ''}`}
          onClick={() => setActiveTab('profile')}
        >
          Perfil
        </button>
        <button
          className={`tab ${activeTab === 'progress' ? 'active' : ''}`}
          onClick={() => setActiveTab('progress')}
        >
          Progresso
        </button>
        <button
          className={`tab ${activeTab === 'workouts' ? 'active' : ''}`}
          onClick={() => setActiveTab('workouts')}
        >
          Treinos
        </button>
        <button
          className={`tab ${activeTab === 'meals' ? 'active' : ''}`}
          onClick={() => setActiveTab('meals')}
        >
          Refeições
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'profile' && (
          <div className="card">
            <h2>Informações do Cliente</h2>
            <p>Cadastro realizado em: {new Date(client.createdAt).toLocaleDateString('pt-BR')}</p>
          </div>
        )}

        {activeTab === 'progress' && (
          <>
            <ProgressChart clientId={clientId} />
            <AnalyticsChart clientId={clientId} />
          </>
        )}

        {activeTab === 'workouts' && (
          <div>
            <WorkoutExecutionForm clientId={clientId} onSuccess={loadClientData} />

            <div className="card">
              <h2>Histórico de Treinos</h2>
              {workoutExecutions.length === 0 ? (
                <p>Nenhum treino registrado</p>
              ) : (
                <div className="executions-list">
                  {workoutExecutions.map(execution => (
                    <div key={execution.id} className="execution-card">
                      <div className="execution-header">
                        <h4>{execution.exerciseName}</h4>
                        <span className="date">{new Date(execution.date).toLocaleDateString('pt-BR')}</span>
                      </div>
                      <div className="execution-details">
                        <p>Series: {execution.sets} × Reps: {execution.reps} | Peso: {execution.weight}kg</p>
                        <p>Sensação: {execution.feeling}</p>
                      </div>
                      <span className={`status ${execution.status.toLowerCase()}`}>
                        {execution.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'meals' && (
          <div>
            <MealExecutionForm clientId={clientId} onSuccess={loadClientData} />

            <div className="card">
              <h2>Histórico de Refeições</h2>
              {mealExecutions.length === 0 ? (
                <p>Nenhuma refeição registrada</p>
              ) : (
                <div className="executions-list">
                  {mealExecutions.map(meal => (
                    <div key={meal.id} className="execution-card">
                      <div className="execution-header">
                        <h4>{meal.mealName}</h4>
                        <span className="date">{new Date(meal.date).toLocaleDateString('pt-BR')}</span>
                      </div>
                      <div className="execution-details">
                        <p>
                          Proteína: {meal.protein}g | Carboidrato: {meal.carbs}g | Gordura: {meal.fat}g
                        </p>
                        <p>Calorias: {meal.calories} kcal</p>
                      </div>
                      <span className={`status ${meal.completed ? 'completed' : 'partial'}`}>
                        {meal.completed ? '✅ Concluído' : '⏸️ Parcial'}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
