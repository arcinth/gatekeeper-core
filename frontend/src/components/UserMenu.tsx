import { useEffect, useRef, useState } from 'react'
import { ChevronDown, LogOut } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return (first + last).toUpperCase() || '?'
}

export function UserMenu() {
  const { user, logout } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) {
      return
    }
    function handlePointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  if (!user) {
    return null
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((current) => !current)}
        aria-expanded={isOpen}
        aria-haspopup="menu"
        className="flex items-center gap-2 rounded-md px-1.5 py-1.5 text-sm transition-colors hover:bg-surface-2"
      >
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-accent-line bg-accent-bg font-mono text-[11px] font-semibold text-accent-hi">
          {initials(user.fullName)}
        </span>
        <span className="hidden text-left sm:block">
          <span className="block truncate text-[13px] font-medium leading-tight text-content">{user.fullName}</span>
          <span className="block truncate font-mono text-[10px] uppercase leading-tight tracking-wide text-faint">
            {user.roleName}
          </span>
        </span>
        <ChevronDown className="h-4 w-4 shrink-0 text-faint" aria-hidden="true" />
      </button>

      {isOpen && (
        <div
          role="menu"
          className="absolute right-0 z-50 mt-2 w-60 overflow-hidden rounded-lg border border-line bg-surface py-1 shadow-xl"
        >
          <div className="border-b border-line-soft px-3 py-2.5">
            <p className="truncate text-sm font-medium text-content">{user.fullName}</p>
            <p className="truncate text-xs text-muted">{user.email}</p>
            <p className="mt-1 truncate font-mono text-[10px] uppercase tracking-wide text-faint">
              {user.organizationName}
            </p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => void logout()}
            className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm text-muted transition-colors hover:bg-surface-2 hover:text-content"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Log out
          </button>
        </div>
      )}
    </div>
  )
}
