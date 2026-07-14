import { apiClient } from './apiClient'
import { tokenStorage } from './tokenStorage'
import type { ApiResponse } from '../types/api'
import type { CurrentUser, LoginRequest, TokenResponse } from '../types/auth'

export const authService = {
  async login(request: LoginRequest): Promise<TokenResponse> {
    const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', request)
    const tokens = response.data.data
    tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken)
    return tokens
  },

  async logout(): Promise<void> {
    const refreshToken = tokenStorage.getRefreshToken()
    if (refreshToken) {
      try {
        await apiClient.post('/auth/logout', { refreshToken })
      } finally {
        tokenStorage.clear()
      }
    } else {
      tokenStorage.clear()
    }
  },

  async getCurrentUser(): Promise<CurrentUser> {
    const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
    return response.data.data
  },
}
