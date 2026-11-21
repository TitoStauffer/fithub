import { useState, useEffect } from 'react'
import { apiClient } from '../services/api'
import { useAuth } from '../context/AuthContext'
import './Dashboard.css'

export default function ProfessionalDashboard() {
  const { user } = useAuth()
  const [stats, setStats] = useState({
    totalClients: 0,
    activeClients: 0,
    totalWorkouts: 0,
    totalMeals: 0
  })
  const [clients, setClients] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadData()
  }, [])

  async function loadData() {
    try {
      const response = await apiClient.get('/patients')
      const patients = response.data

      setClients(patients)
      setStats({
        totalClients: patients.length,
        activeClients: patients.filter(p => p.status === 'ACTIVE').length,
        totalWorkouts: 0, // TODO: fetch from API
        totalMeals: 0 // TODO: fetch from API
      })
    } catch (error) {
      console.error('Error loading data:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Carregando...</div>
  }

  return (
    <div className="container">
      <div className="dashboard-header">
        <h1>Dashboard</h1>
        <p>Bem-vindo, {user?.name}</p>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon">👥</div>
          <div className="stat-content">
            <h3>Total de Clientes</h3>
            <p className="stat-number">{stats.totalClients}</p>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">✅</div>
          <div className="stat-content">
            <h3>Clientes Ativos</h3>
            <p className="stat-number">{stats.activeClients}</p>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">💪</div>
          <div className="stat-content">
            <h3>Execuções de Treino</h3>
            <p className="stat-number">{stats.totalWorkouts}</p>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">🥗</div>
          <div className="stat-content">
            <h3>Refeições Registradas</h3>
            <p className="stat-number">{stats.totalMeals}</p>
          </div>
        </div>
      </div>

      <div className="recent-clients">
        <h2>Clientes Recentes</h2>
        <div className="clients-list">
          {clients.map(client => (
            <div key={client.id} className="client-item">
              <div className="client-info">
                <h4>{client.name}</h4>
                <p>IMC: {client.imc} | Peso: {client.weight}kg</p>
              </div>
              <div className="client-status">
                <span className={`status ${client.status.toLowerCase()}`}>
                  {client.status === 'ACTIVE' ? '✅ Ativo' : '⏸️ Inativo'}
                </span>
                <a href={`/clients/${client.id}`} className="btn-primary">
                  Ver Detalhes
                </a>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
