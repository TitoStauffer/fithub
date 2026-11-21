import { Outlet, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './Layout.css'

export default function Layout() {
  const { user, logout } = useAuth()

  return (
    <div className="layout">
      <nav className="navbar">
        <div className="nav-brand">
          <Link to="/">💪 FitHub</Link>
        </div>

        <ul className="nav-links">
          <li><Link to="/">Dashboard</Link></li>
          <li><Link to="/patients">Pacientes</Link></li>
          <li><Link to="/training">Treinos</Link></li>
          <li><Link to="/nutrition">Nutrição</Link></li>
        </ul>

        <div className="nav-user">
          <span>{user?.name}</span>
          <button className="btn-primary" onClick={logout}>Sair</button>
        </div>
      </nav>

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  )
}
