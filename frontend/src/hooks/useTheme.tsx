import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type ThemePreference = 'dark' | 'light' | 'system'

const STORAGE_KEY = 'gatekeeper.theme'

interface ThemeContextValue {
  preference: ThemePreference
  /** The theme actually rendering right now, with 'system' already resolved. */
  resolved: 'dark' | 'light'
  setPreference: (preference: ThemePreference) => void
  toggle: () => void
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined)

function readStoredPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'dark' || stored === 'light' || stored === 'system') {
      return stored
    }
  } catch {
    // Private-browsing storage restrictions must never break rendering - the
    // app simply falls back to following the system preference.
  }
  return 'system'
}

function systemTheme(): 'dark' | 'light' {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readStoredPreference)
  const [systemResolved, setSystemResolved] = useState<'dark' | 'light'>(systemTheme)

  // Track OS-level changes so a 'system' preference stays live.
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: light)')
    function handleChange() {
      setSystemResolved(media.matches ? 'light' : 'dark')
    }
    media.addEventListener('change', handleChange)
    return () => media.removeEventListener('change', handleChange)
  }, [])

  const resolved = preference === 'system' ? systemResolved : preference

  // index.css keys off data-theme; leaving the attribute off entirely for
  // 'system' lets the prefers-color-scheme media query own the decision.
  useEffect(() => {
    const root = document.documentElement
    if (preference === 'system') {
      root.removeAttribute('data-theme')
    } else {
      root.setAttribute('data-theme', preference)
    }
  }, [preference])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Preference persistence is a nice-to-have, not app state.
    }
  }, [])

  const toggle = useCallback(() => {
    setPreference(resolved === 'dark' ? 'light' : 'dark')
  }, [resolved, setPreference])

  const value = useMemo(
    () => ({ preference, resolved, setPreference, toggle }),
    [preference, resolved, setPreference, toggle],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider.')
  }
  return context
}
