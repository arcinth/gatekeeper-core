import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { authService } from '../services/authService'
import { tokenStorage } from '../services/tokenStorage'
import type { CurrentUser, LoginRequest } from '../types/auth'

interface AuthContextValue {
  user: CurrentUser | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (request: LoginRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const loadCurrentUser = useCallback(async () => {
    if (!tokenStorage.getAccessToken()) {
      setUser(null)
      setIsLoading(false)
      return
    }
    try {
      const currentUser = await authService.getCurrentUser()
      setUser(currentUser)
    } catch {
      tokenStorage.clear()
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadCurrentUser()
  }, [loadCurrentUser])

  const login = useCallback(async (request: LoginRequest) => {
    await authService.login(request)
    await loadCurrentUser()
  }, [loadCurrentUser])

  const logout = useCallback(async () => {
    await authService.logout()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, isLoading, isAuthenticated: user !== null, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider.')
  }
  return context
}
