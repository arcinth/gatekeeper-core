import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { PanelLeftClose, PanelLeftOpen, ShieldCheck } from 'lucide-react'
import { NAV_SECTIONS } from '../config/navigation'

const COLLAPSE_STORAGE_KEY = 'gatekeeper.sidebar.collapsed'

/**
 * Persistent left navigation - the fix for "no in-app navigation, relies on
 * the browser back button" (Milestone 3). Desktop collapse-to-icon-rail state
 * is sticky across reloads via localStorage since it's a personal density
 * preference, not app state; mobile open/close is controlled by AppLayout
 * (the toggle button lives in the top bar, a sibling component).
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
        // Sidebar collapse is a nice-to-have preference, not app state - a
        // failed write (e.g. private-browsing storage restrictions) should
        // never break navigation itself, just fail to persist next time.
      }
      return next
    })
  }

  return (
    <>
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-30 bg-slate-900/50 lg:hidden"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-slate-200 bg-white transition-transform duration-200 ease-in-out lg:sticky lg:top-0 lg:h-screen lg:translate-x-0 ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full'
        } ${isCollapsed ? 'lg:w-16' : 'lg:w-64'}`}
      >
        <div className="flex h-14 shrink-0 items-center gap-2 border-b border-slate-200 px-4">
          <ShieldCheck className="h-6 w-6 shrink-0 text-blue-600" aria-hidden="true" />
          {!isCollapsed && <span className="truncate text-base font-semibold text-slate-900">GateKeeper</span>}
        </div>

        <nav className="flex-1 overflow-y-auto px-2 py-4">
          {NAV_SECTIONS.map((section) => (
            <div key={section.label} className="mb-4">
              {!isCollapsed && (
                <p className="mb-1 px-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                  {section.label}
                </p>
              )}
              <ul className="flex flex-col gap-0.5">
                {section.items.map((item) => (
                  <li key={item.path}>
                    <NavLink
                      to={item.path}
                      onClick={onCloseMobile}
                      title={isCollapsed ? item.label : undefined}
                      className={({ isActive }) =>
                        `flex items-center gap-3 rounded-md border-l-2 px-2.5 py-2 text-sm font-medium transition-colors ${
                          isActive
                            ? 'border-blue-600 bg-blue-50 text-blue-700'
                            : 'border-transparent text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                        }`
                      }
                    >
                      <item.icon className="h-5 w-5 shrink-0" aria-hidden="true" />
                      {!isCollapsed && <span className="truncate">{item.label}</span>}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        <button
          type="button"
          onClick={toggleCollapsed}
          className="hidden shrink-0 items-center gap-2 border-t border-slate-200 px-4 py-3 text-sm text-slate-500 hover:bg-slate-100 hover:text-slate-900 lg:flex"
        >
          {isCollapsed ? (
            <PanelLeftOpen className="h-5 w-5" aria-hidden="true" />
          ) : (
            <>
              <PanelLeftClose className="h-5 w-5" aria-hidden="true" />
              <span>Collapse</span>
            </>
          )}
        </button>
      </aside>
    </>
  )
}
