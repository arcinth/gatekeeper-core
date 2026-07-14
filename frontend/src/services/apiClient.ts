import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse, ApiErrorResponse } from '../types/api'
import type { TokenResponse } from '../types/auth'
import { tokenStorage } from './tokenStorage'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

export const apiClient = axios.create({ baseURL })

apiClient.interceptors.request.use((config) => {
  const accessToken = tokenStorage.getAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

let refreshInFlight: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available.')
  }
  const response = await axios.post<ApiResponse<TokenResponse>>(
    `${baseURL}/auth/refresh`,
    { refreshToken },
  )
  const tokens = response.data.data
  tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken)
  return tokens.accessToken
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined

    const isAuthEndpoint = originalRequest?.url?.includes('/auth/')
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true
      try {
        refreshInFlight ??= refreshAccessToken().finally(() => {
          refreshInFlight = null
        })
        const newAccessToken = await refreshInFlight
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return apiClient(originalRequest)
      } catch {
        tokenStorage.clear()
        window.location.href = '/login'
      }
    }

    return Promise.reject(error)
  },
)
