import type { ReactNode } from 'react'
import { ShieldCheck } from 'lucide-react'

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mb-6 flex flex-col items-center gap-2">
          <ShieldCheck className="h-8 w-8 text-blue-600" aria-hidden="true" />
          <h1 className="text-2xl font-semibold text-slate-900">GateKeeper</h1>
        </div>
        {children}
      </div>
    </div>
  )
}
