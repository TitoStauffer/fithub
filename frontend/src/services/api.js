import axios from 'axios'

export const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
})

// Add token to requests
const token = localStorage.getItem('accessToken')
if (token) {
  apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`
}

// Interceptor for token refresh
apiClient.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        try {
          // Implement refresh token logic here
          // For now, just logout
          localStorage.removeItem('accessToken')
          window.location.href = '/login'
        } catch (err) {
          return Promise.reject(err)
        }
      }
    }

    return Promise.reject(error)
  }
)
