import type { ReactNode } from 'react'
import { useAuth } from '../hooks/useAuth'

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
        <span className="text-lg font-semibold text-slate-900">GateKeeper</span>
        <div className="flex items-center gap-4 text-sm text-slate-600">
          {user && (
            <span>
              {user.fullName} &middot; {user.roleName}
            </span>
          )}
          <button
            onClick={() => void logout()}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            Log out
          </button>
        </div>
      </header>
      <main className="p-6">{children}</main>
    </div>
  )
}
