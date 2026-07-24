import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { PanelLeftClose, PanelLeftOpen, ShieldCheck } from 'lucide-react'
import { NAV_ITEMS } from '../config/navigation'

const COLLAPSE_STORAGE_KEY = 'gatekeeper.sidebar.collapsed'

/**
 * Persistent left rail. Now a flat six-item list rather than four labelled
 * sections: with the schema-shaped entity pages gone there is little left to
 * group, and a short flat rail is faster to scan than a grouped long one.
 * Collapse-to-icon state persists as a personal density preference.
 */
export function Sidebar({ isMobileOpen, onCloseMobile }: { isMobileOpen: boolean; onCloseMobile: () => void }) {
  const [isCollapsed, setIsCollapsed] = useState(() => {
    try {
      return localStorage.getItem(COLLAPSE_STORAGE_KEY) === 'true'
    } catch {
      return false
    }
  })

  function toggleCollapsed() {
    setIsCollapsed((current) => {
      const next = !current
      try {
        localStorage.setItem(COLLAPSE_STORAGE_KEY, String(next))
      } catch {
        // A failed write should never break navigation, just fail to persist.
      }
      return next
    })
  }

  return (
    <>
      {isMobileOpen && (
        <div className="fixed inset-0 z-30 bg-black/60 lg:hidden" onClick={onCloseMobile} aria-hidden="true" />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-line bg-surface transition-transform duration-200 ease-out lg:sticky lg:top-0 lg:h-screen lg:translate-x-0 ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full'
        } ${isCollapsed ? 'lg:w-16' : 'lg:w-60'}`}
      >
        <div className="flex h-14 shrink-0 items-center gap-2.5 border-b border-line px-4">
          <span
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md border border-accent-line bg-accent-bg text-accent-hi"
            aria-hidden="true"
          >
            <ShieldCheck className="h-4 w-4" />
          </span>
          {!isCollapsed && (
            <span className="truncate text-[15px] font-semibold tracking-tight text-content">GateKeeper</span>
          )}
        </div>

        <nav className="flex-1 overflow-y-auto p-2" aria-label="Main">
          <ul className="flex flex-col gap-0.5">
            {NAV_ITEMS.map((item) => (
              <li key={item.path} className={item.startsGroup ? 'mt-2 border-t border-line-soft pt-2' : undefined}>
                <NavLink
                  to={item.path}
                  onClick={onCloseMobile}
                  title={isCollapsed ? item.label : undefined}
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium transition-colors ${
                      isActive
                        ? 'bg-accent-bg text-accent-hi'
                        : 'text-muted hover:bg-surface-2 hover:text-content'
                    }`
                  }
                >
                  <item.icon className="h-4.5 w-4.5 shrink-0" aria-hidden="true" />
                  {!isCollapsed && <span className="truncate">{item.label}</span>}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <button
          type="button"
          onClick={toggleCollapsed}
          className="hidden shrink-0 items-center gap-2.5 border-t border-line px-4 py-3 text-sm text-faint transition-colors hover:bg-surface-2 hover:text-content lg:flex"
        >
          {isCollapsed ? (
            <PanelLeftOpen className="h-4.5 w-4.5" aria-hidden="true" />
          ) : (
            <>
              <PanelLeftClose className="h-4.5 w-4.5" aria-hidden="true" />
              <span>Collapse</span>
            </>
          )}
        </button>
      </aside>
    </>
  )
}
