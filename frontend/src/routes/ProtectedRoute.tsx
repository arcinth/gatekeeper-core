import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()

  // A neutral full-page hold while the session is resolved. Deliberately not a
  // skeleton: we do not yet know which page is being restored, so a
  // content-shaped placeholder would guess wrong.
  if (isLoading) {
    return <div className="min-h-screen bg-app" aria-busy="true" aria-label="Loading" />
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
