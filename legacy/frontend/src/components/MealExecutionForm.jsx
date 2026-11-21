import { useState } from 'react'
import { apiClient } from '../services/api'

export default function MealExecutionForm({ clientId, onSuccess }) {
  const [formData, setFormData] = useState({
    mealName: '',
    protein: '',
    carbs: '',
    fat: '',
    completed: true
  })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)

  function calculateCalories(protein, carbs, fat) {
    return (protein * 4) + (carbs * 4) + (fat * 9)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setLoading(true)

    try {
      await apiClient.post(`/patients/${clientId}/nutrition/executions/record`, formData)
      setSuccess('Refeição registrada com sucesso!')
      setFormData({
        mealName: '',
        protein: '',
        carbs: '',
        fat: '',
        completed: true
      })
      onSuccess?.()
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.response?.data?.error || 'Erro ao registrar refeição'
      setError(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  const calories = calculateCalories(
    parseFloat(formData.protein) || 0,
    parseFloat(formData.carbs) || 0,
    parseFloat(formData.fat) || 0
  )

  return (
    <div className="card">
      <h2>Registrar Refeição</h2>

      {error && <div className="error-box">{error}</div>}
      {success && <div className="success">{success}</div>}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Nome da Refeição</label>
          <input
            type="text"
            placeholder="Ex: Café da manhã, Almoço"
            value={formData.mealName}
            onChange={(e) => setFormData({...formData, mealName: e.target.value})}
            required
          />
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Proteína (g)</label>
            <input
              type="number"
              step="0.1"
              min="0"
              value={formData.protein}
              onChange={(e) => setFormData({...formData, protein: e.target.value})}
              required
            />
          </div>

          <div className="form-group">
            <label>Carboidrato (g)</label>
            <input
              type="number"
              step="0.1"
              min="0"
              value={formData.carbs}
              onChange={(e) => setFormData({...formData, carbs: e.target.value})}
              required
            />
          </div>

          <div className="form-group">
            <label>Gordura (g)</label>
            <input
              type="number"
              step="0.1"
              min="0"
              value={formData.fat}
              onChange={(e) => setFormData({...formData, fat: e.target.value})}
              required
            />
          </div>

          <div className="form-group">
            <label>Calorias (kcal)</label>
            <input
              type="number"
              value={calories.toFixed(0)}
              disabled
              className="input-disabled"
            />
          </div>
        </div>

        <div className="form-group">
          <label>
            <input
              type="checkbox"
              checked={formData.completed}
              onChange={(e) => setFormData({...formData, completed: e.target.checked})}
            />
            Refeição completada
          </label>
        </div>

        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? 'Registrando...' : 'Registrar Refeição'}
        </button>
      </form>
    </div>
  )
}
