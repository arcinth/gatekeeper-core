import { useEffect, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { Menu } from 'lucide-react'
import { Sidebar } from '../components/Sidebar'
import { Breadcrumbs, type BreadcrumbItem } from '../components/Breadcrumbs'
import { UserMenu } from '../components/UserMenu'
import { PageHeader } from '../components/ui/PageHeader'

interface AppLayoutProps {
  children: ReactNode
  /** Omit entirely for loading/error/not-found early-return states, where no page header should render. */
  title?: ReactNode
  eyebrow?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  breadcrumbs?: BreadcrumbItem[]
}

/**
 * The persistent application shell (Milestone 3): sidebar + top bar +
 * breadcrumbs + page header, present on every authenticated page so
 * navigation never depends on the browser back button. Replaces the old
 * AppLayout, which was just a bare header with no navigation at all.
 */
export function AppLayout({ children, title, eyebrow, description, actions, breadcrumbs }: AppLayoutProps) {
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false)
  const location = useLocation()

  useEffect(() => {
    setIsMobileNavOpen(false)
  }, [location.pathname])

  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar isMobileOpen={isMobileNavOpen} onCloseMobile={() => setIsMobileNavOpen(false)} />

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center justify-between gap-4 border-b border-slate-200 bg-white px-4 sm:px-6">
          <button
            type="button"
            onClick={() => setIsMobileNavOpen(true)}
            className="rounded-md p-2 text-slate-500 hover:bg-slate-100 lg:hidden"
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" aria-hidden="true" />
          </button>
          <div className="flex-1" />
          <UserMenu />
        </header>

        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
          {title && <PageHeader title={title} eyebrow={eyebrow} description={description} actions={actions} />}
          {children}
        </main>
      </div>
    </div>
  )
}
