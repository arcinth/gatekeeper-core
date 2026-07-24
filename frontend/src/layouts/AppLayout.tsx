import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ChevronRight, Menu } from 'lucide-react'
import { Sidebar } from '../components/Sidebar'
import { UserMenu } from '../components/UserMenu'
import { ThemeToggle } from '../components/ThemeToggle'

export interface BreadcrumbItem {
  label: string
  to?: string
}

interface AppLayoutProps {
  children: ReactNode
  /** Omitted on loading/error states, where no page header should render. */
  title?: ReactNode
  eyebrow?: string
  description?: ReactNode
  actions?: ReactNode
  breadcrumbs?: BreadcrumbItem[]
  /** Detail pages read better narrower; lists use the full width. */
  width?: 'wide' | 'narrow'
}

/**
 * The application shell: rail + top bar + page header. Every authenticated
 * screen renders through it, so header shape, breadcrumb placement and
 * content width are decided in exactly one place (Product Experience spec,
 * §07 "consistent layouts").
 */
export function AppLayout({
  children,
  title,
  eyebrow,
  description,
  actions,
  breadcrumbs,
  width = 'wide',
}: AppLayoutProps) {
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false)
  const location = useLocation()

  useEffect(() => {
    setIsMobileNavOpen(false)
  }, [location.pathname])

  return (
    <div className="flex min-h-screen bg-app">
      <Sidebar isMobileOpen={isMobileNavOpen} onCloseMobile={() => setIsMobileNavOpen(false)} />

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-3 border-b border-line bg-app/85 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            onClick={() => setIsMobileNavOpen(true)}
            className="-ml-1 rounded-md p-2 text-muted transition-colors hover:bg-surface-2 hover:text-content lg:hidden"
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" aria-hidden="true" />
          </button>

          {breadcrumbs && breadcrumbs.length > 0 && (
            <nav aria-label="Breadcrumb" className="hidden min-w-0 items-center text-sm sm:flex">
              {breadcrumbs.map((item, index) => {
                const isLast = index === breadcrumbs.length - 1
                return (
                  <span key={`${item.label}-${index}`} className="flex min-w-0 items-center">
                    {index > 0 && (
                      <ChevronRight className="mx-1 h-3.5 w-3.5 shrink-0 text-faint" aria-hidden="true" />
                    )}
                    {item.to && !isLast ? (
                      <Link to={item.to} className="truncate text-muted transition-colors hover:text-content">
                        {item.label}
                      </Link>
                    ) : (
                      <span className={`truncate ${isLast ? 'text-content' : 'text-muted'}`}>{item.label}</span>
                    )}
                  </span>
                )
              })}
            </nav>
          )}

          <div className="flex-1" />
          <ThemeToggle />
          <UserMenu />
        </header>

        <main className="min-w-0 flex-1 px-4 py-7 sm:px-6 lg:px-8">
          <div className={width === 'narrow' ? 'mx-auto w-full max-w-5xl' : 'mx-auto w-full max-w-350'}>
            {title && (
              <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  {eyebrow && (
                    <p className="mb-1.5 font-mono text-[10px] uppercase tracking-[0.14em] text-accent">{eyebrow}</p>
                  )}
                  <h1 className="truncate text-2xl font-semibold tracking-tight text-content">{title}</h1>
                  {description && <p className="mt-1.5 max-w-2xl text-sm text-muted">{description}</p>}
                </div>
                {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
              </div>
            )}
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}
