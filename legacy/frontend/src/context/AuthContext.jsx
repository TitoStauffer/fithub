import { createContext, useContext, useState, useEffect } from 'react'
import { apiClient } from '../services/api'

const AuthContext = createContext()

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      validateToken(token)
    } else {
      setLoading(false)
    }

    // Configurar interceptor para refresh automático
    setupAxiosInterceptor()
  }, [])

  async function validateToken(token) {
    try {
      apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`
      const response = await apiClient.get('/auth/me')
      setUser(response.data)
    } catch (error) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      delete apiClient.defaults.headers.common['Authorization']
    } finally {
      setLoading(false)
    }
  }

  async function login(email, password) {
    const response = await apiClient.post('/auth/login', { email, password })
    const { accessToken, refreshToken } = response.data

    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)

    apiClient.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`

    // Fetch complete user data
    try {
      const userResponse = await apiClient.get('/auth/me')
      setUser(userResponse.data)
      return userResponse.data
    } catch (error) {
      throw error
    }
  }

  async function register(email, password, name, role) {
    const response = await apiClient.post('/auth/register', {
      email,
      password,
      name,
      role
    })
    return response.data
  }

  async function refreshTokenSilently() {
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) {
      logout()
      return null
    }

    try {
      setIsRefreshing(true)
      const response = await apiClient.post('/auth/refresh', {
        refreshToken
      })

      const { accessToken, refreshToken: newRefreshToken } = response.data
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', newRefreshToken)
      apiClient.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`

      return accessToken
    } catch (error) {
      logout()
      return null
    } finally {
      setIsRefreshing(false)
    }
  }

  function setupAxiosInterceptor() {
    apiClient.interceptors.response.use(
      (response) => response,
      async (error) => {
        if (error.response?.status === 401 && !isRefreshing) {
          const newToken = await refreshTokenSilently()
          if (newToken) {
            error.config.headers['Authorization'] = `Bearer ${newToken}`
            return apiClient.request(error.config)
          }
        }
        return Promise.reject(error)
      }
    )
  }

  function logout() {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    setUser(null)
    delete apiClient.defaults.headers.common['Authorization']
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshTokenSilently }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
