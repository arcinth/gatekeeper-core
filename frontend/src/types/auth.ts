export interface LoginRequest {
  email: string
  password: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

export interface CurrentUser {
  id: number
  email: string
  fullName: string
  roleName: string
  organizationId: number
  organizationName: string
}
