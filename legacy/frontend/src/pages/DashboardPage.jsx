import { useAuth } from '../context/AuthContext'
import './Pages.css'

export default function DashboardPage() {
  const { user } = useAuth()

  return (
    <div className="container">
      <h1>Bem-vindo, {user?.name}! 👋</h1>

      <div className="dashboard-grid">
        <div className="card">
          <h3>👥 Pacientes</h3>
          <p>Gerenciar pacientes e acompanhamento</p>
          <a href="/patients" className="btn-primary">Ir</a>
        </div>

        <div className="card">
          <h3>💪 Treinos</h3>
          <p>Criar e acompanhar planos de treino</p>
          <a href="/training" className="btn-primary">Ir</a>
        </div>

        <div className="card">
          <h3>🥗 Nutrição</h3>
          <p>Prescrever e acompanhar planos alimentares</p>
          <a href="/nutrition" className="btn-primary">Ir</a>
        </div>
      </div>

      <div className="card">
        <h2>Status</h2>
        <p>Role: <strong>{user?.role}</strong></p>
        <p>Email: <strong>{user?.email}</strong></p>
      </div>
    </div>
  )
}
