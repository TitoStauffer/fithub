import { useState } from 'react'
import { apiClient } from '../services/api'

export default function WorkoutExecutionForm({ clientId, onSuccess }) {
  const [formData, setFormData] = useState({
    exerciseName: '',
    sets: '',
    reps: '',
    weight: '',
    feeling: 'Bom',
    status: 'COMPLETED'
  })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setLoading(true)

    try {
      await apiClient.post(`/patients/${clientId}/training/executions/record`, formData)
      setSuccess('Treino registrado com sucesso!')
      setFormData({
        exerciseName: '',
        sets: '',
        reps: '',
        weight: '',
        feeling: 'Bom',
        status: 'COMPLETED'
      })
      onSuccess?.()
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.response?.data?.error || 'Erro ao registrar treino'
      setError(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="card">
      <h2>Registrar Treino</h2>

      {error && <div className="error-box">{error}</div>}
      {success && <div className="success">{success}</div>}

      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <div className="form-group">
            <label>Exercício</label>
            <input
              type="text"
              placeholder="Ex: Supino, Agachamento"
              value={formData.exerciseName}
              onChange={(e) => setFormData({...formData, exerciseName: e.target.value})}
              required
            />
          </div>

          <div className="form-group">
            <label>Séries</label>
            <input
              type="number"
              min="1"
              value={formData.sets}
              onChange={(e) => setFormData({...formData, sets: e.target.value})}
              required
            />
          </div>

          <div className="form-group">
            <label>Repetições</label>
            <input
              type="number"
              min="1"
              value={formData.reps}
              onChange={(e) => setFormData({...formData, reps: e.target.value})}
              required
            />
          </div>

          <div className="form-group">
            <label>Peso (kg)</label>
            <input
              type="number"
              step="0.5"
              min="0"
              value={formData.weight}
              onChange={(e) => setFormData({...formData, weight: e.target.value})}
              required
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Sensação</label>
            <select
              value={formData.feeling}
              onChange={(e) => setFormData({...formData, feeling: e.target.value})}
            >
              <option value="Muito Difícil">😫 Muito Difícil</option>
              <option value="Difícil">😤 Difícil</option>
              <option value="Bom">😊 Bom</option>
              <option value="Ótimo">🎉 Ótimo</option>
              <option value="Fácil">😄 Fácil</option>
            </select>
          </div>

          <div className="form-group">
            <label>Status</label>
            <select
              value={formData.status}
              onChange={(e) => setFormData({...formData, status: e.target.value})}
            >
              <option value="COMPLETED">✅ Completado</option>
              <option value="PARTIAL">⏸️ Parcial</option>
              <option value="SKIPPED">❌ Não fez</option>
            </select>
          </div>
        </div>

        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? 'Registrando...' : 'Registrar Treino'}
        </button>
      </form>
    </div>
  )
}
