import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { ShieldCheck } from 'lucide-react'
import { Input, Label } from '../components/ui/Field'
import { ErrorState } from '../components/ui/states'
import { buttonClasses } from '../components/ui/buttonClasses'
import { useAuth } from '../hooks/useAuth'
import type { ApiErrorResponse } from '../types/api'

/**
 * Split layout (Product Experience spec, §07): the form on one side, a single
 * confident statement of what GateKeeper does on the other. The old screen was
 * a bare form on a blank canvas - functionally fine, but it said nothing at
 * the product's first impression.
 */
export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await login({ email, password })
      navigate('/inbox', { replace: true })
    } catch (err) {
      if (axios.isAxiosError<ApiErrorResponse>(err) && err.response?.data?.error) {
        setError(err.response.data.error.message)
      } else {
        setError('Unable to log in. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-screen bg-app lg:grid-cols-2">
      {/* The statement. Hidden on small screens, where the form is the only job. */}
      <section className="relative hidden flex-col justify-between overflow-hidden border-r border-line bg-surface p-12 lg:flex">
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(600px 300px at 80% 0%, var(--accent-bg), transparent 60%), radial-gradient(500px 250px at 0% 100%, var(--info-bg), transparent 60%)',
          }}
          aria-hidden="true"
        />

        <div className="relative flex items-center gap-2.5">
          <span
            className="flex h-8 w-8 items-center justify-center rounded-md border border-accent-line bg-accent-bg text-accent-hi"
            aria-hidden="true"
          >
            <ShieldCheck className="h-4.5 w-4.5" />
          </span>
          <span className="text-base font-semibold tracking-tight text-content">GateKeeper</span>
        </div>

        <div className="relative max-w-md">
          <p className="mb-4 font-mono text-[11px] uppercase tracking-[0.16em] text-accent">Policy-first governance</p>
          <h1 className="text-4xl font-semibold leading-[1.1] tracking-tight text-content">
            Every pull request, checked before it merges.
          </h1>
          <p className="mt-5 text-sm leading-relaxed text-muted">
            Deterministic policy and security engines decide what ships. AI reviews advise, and never override a
            governance decision.
          </p>
        </div>

        <div className="relative flex flex-wrap gap-x-6 gap-y-2 font-mono text-[11px] text-faint">
          <span>Policy Engine</span>
          <span>Security Engine</span>
          <span>AI Advisory</span>
          <span>Audit Trail</span>
        </div>
      </section>

      {/* The form. */}
      <section className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <span
              className="flex h-8 w-8 items-center justify-center rounded-md border border-accent-line bg-accent-bg text-accent-hi"
              aria-hidden="true"
            >
              <ShieldCheck className="h-4.5 w-4.5" />
            </span>
            <span className="text-base font-semibold tracking-tight text-content">GateKeeper</span>
          </div>

          <h2 className="text-2xl font-semibold tracking-tight text-content">Sign in</h2>
          <p className="mt-1.5 text-sm text-muted">Use your GateKeeper account to continue.</p>

          <form onSubmit={(event) => void handleSubmit(event)} className="mt-7 flex flex-col gap-4">
            <div>
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>

            <div>
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                required
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            {error && <ErrorState message={error} />}

            <button type="submit" className={buttonClasses('primary', 'md', 'mt-1 w-full')} disabled={isSubmitting}>
              {isSubmitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>
      </section>
    </div>
  )
}
