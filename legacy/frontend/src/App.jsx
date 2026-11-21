import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import ProfessionalDashboard from './pages/ProfessionalDashboard'
import ClientDetailPage from './pages/ClientDetailPage'
import PatientsPage from './pages/PatientsPage'
import TrainingPage from './pages/TrainingPage'
import NutritionPage from './pages/NutritionPage'
import Layout from './components/Layout'

export default function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return <div className="loading">Carregando...</div>
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {user ? (
        <Route element={<Layout />}>
          <Route path="/" element={<ProfessionalDashboard />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/patients" element={<PatientsPage />} />
          <Route path="/clients/:clientId" element={<ClientDetailPage />} />
          <Route path="/training" element={<TrainingPage />} />
          <Route path="/nutrition" element={<NutritionPage />} />
        </Route>
      ) : (
        <Route path="*" element={<Navigate to="/login" replace />} />
      )}
    </Routes>
  )
}
