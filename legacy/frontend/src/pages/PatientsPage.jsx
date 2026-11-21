import { useState } from 'react'
import { apiClient } from '../services/api'
import './Pages.css'

export default function PatientsPage() {
  const [patients, setPatients] = useState([])
  const [showForm, setShowForm] = useState(false)
  const [formData, setFormData] = useState({
    userId: '',
    name: '',
    weight: '',
    age: '',
    height: ''
  })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  async function handleCreatePatient(e) {
    e.preventDefault()
    setError('')
    setSuccess('')

    try {
      await apiClient.post('/patients', formData)
      setSuccess('Paciente criado com sucesso!')
      setFormData({ userId: '', name: '', weight: '', age: '', height: '' })
      setShowForm(false)
      // Reload patients
    } catch (err) {
      setError(err.response?.data?.message || 'Erro ao criar paciente')
    }
  }

  return (
    <div className="container">
      <div className="page-header">
        <h1>Pacientes</h1>
        <button className="btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '❌ Cancelar' : '➕ Novo Paciente'}
        </button>
      </div>

      {error && <div className="error-box">{error}</div>}
      {success && <div className="success">{success}</div>}

      {showForm && (
        <div className="card">
          <h2>Novo Paciente</h2>
          <form onSubmit={handleCreatePatient}>
            <div className="form-group">
              <label>ID do Usuário</label>
              <input
                type="text"
                value={formData.userId}
                onChange={(e) => setFormData({...formData, userId: e.target.value})}
                required
              />
            </div>

            <div className="form-group">
              <label>Nome</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({...formData, name: e.target.value})}
                required
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label>Peso (kg)</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.weight}
                  onChange={(e) => setFormData({...formData, weight: e.target.value})}
                  required
                />
              </div>

              <div className="form-group">
                <label>Idade</label>
                <input
                  type="number"
                  value={formData.age}
                  onChange={(e) => setFormData({...formData, age: e.target.value})}
                  required
                />
              </div>

              <div className="form-group">
                <label>Altura (cm)</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.height}
                  onChange={(e) => setFormData({...formData, height: e.target.value})}
                  required
                />
              </div>
            </div>

            <button type="submit" className="btn-primary">Criar Paciente</button>
          </form>
        </div>
      )}

      <div className="card">
        <h2>Meus Pacientes</h2>
        {patients.length === 0 ? (
          <p>Nenhum paciente cadastrado</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Nome</th>
                <th>Peso</th>
                <th>Idade</th>
                <th>IMC</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {patients.map(p => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.weight} kg</td>
                  <td>{p.age} anos</td>
                  <td>{p.imc.toFixed(2)}</td>
                  <td>
                    <button className="btn-secondary">Editar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
