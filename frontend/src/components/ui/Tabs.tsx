import type { ReactNode } from 'react'
import type { Tone } from './tones'

export interface TabItem {
  id: string
  label: string
  count?: number
  tone?: Tone
  /** Rendered after the label - used to mark the AI tab as advisory. */
  suffix?: ReactNode
}

const ACTIVE_TONE: Record<Tone, string> = {
  pass: 'border-pass text-content',
  block: 'border-block text-content',
  warn: 'border-warn text-content',
  ai: 'border-ai text-content',
  accent: 'border-accent text-content',
  info: 'border-info text-content',
  neutral: 'border-accent text-content',
}

/**
 * Underlined tabs used for the pull request's Evidence section. The active
 * tab's underline takes the tone of what it contains, which is what keeps the
 * advisory AI lane visually separated from the deterministic engines.
 */
export function Tabs({
  items,
  activeId,
  onChange,
  className = '',
}: {
  items: TabItem[]
  activeId: string
  onChange: (id: string) => void
  className?: string
}) {
  return (
    <div role="tablist" className={`flex gap-1 overflow-x-auto border-b border-line ${className}`}>
      {items.map((item) => {
        const isActive = item.id === activeId
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(item.id)}
            className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-3.5 py-2.5 text-sm font-medium transition-colors ${
              isActive
                ? ACTIVE_TONE[item.tone ?? 'neutral']
                : 'border-transparent text-muted hover:border-line hover:text-content'
            }`}
          >
            {item.label}
            {item.count !== undefined && (
              <span className="tabular rounded-sm bg-surface-3 px-1.5 py-0.5 font-mono text-[10px] text-muted">
                {item.count}
              </span>
            )}
            {item.suffix}
          </button>
        )
      })}
    </div>
  )
}
