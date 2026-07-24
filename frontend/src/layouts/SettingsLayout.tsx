import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { History, Users } from 'lucide-react'
import { AppLayout } from './AppLayout'

const SETTINGS_NAV = [
  { label: 'Access', path: '/settings/access', icon: Users, description: 'People and their roles' },
  { label: 'Audit Log', path: '/settings/audit', icon: History, description: 'Immutable governance history' },
]

/**
 * Administration lives under one destination rather than sitting beside daily
 * work in the main rail (Product Experience spec, §05). Users and the Audit
 * Log are sub-sections here, not top-level peers of the Inbox.
 */
export function SettingsLayout({
  children,
  title,
  description,
  actions,
}: {
  children: ReactNode
  title: string
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <AppLayout eyebrow="Settings" title={title} description={description} actions={actions}>
      <div className="grid gap-6 lg:grid-cols-[200px_minmax(0,1fr)]">
        <nav aria-label="Settings sections" className="lg:sticky lg:top-20 lg:self-start">
          <ul className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
            {SETTINGS_NAV.map((item) => (
              <li key={item.path}>
                <NavLink
                  to={item.path}
                  className={({ isActive }) =>
                    `flex shrink-0 items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                      isActive ? 'bg-accent-bg text-accent-hi' : 'text-muted hover:bg-surface-2 hover:text-content'
                    }`
                  }
                >
                  <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="min-w-0">{children}</div>
      </div>
    </AppLayout>
  )
}
