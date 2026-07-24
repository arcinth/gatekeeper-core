import type { ReactNode } from 'react'
import type { Tone } from './tones'

/**
 * Tinted-glass status chip - the replacement for the old pastel `bg-*-100`
 * pill (Product Experience spec, §09). Low-alpha fill, matching hairline
 * border, mono uppercase text: reads as an instrument rather than a sticker,
 * and stays legible in both themes because every value is a theme token.
 */
const TONE_STYLES: Record<Tone, string> = {
  pass: 'bg-pass-bg text-pass border-pass-line',
  block: 'bg-block-bg text-block border-block-line',
  warn: 'bg-warn-bg text-warn border-warn-line',
  ai: 'bg-ai-bg text-ai border-ai-line',
  accent: 'bg-accent-bg text-accent-hi border-accent-line',
  info: 'bg-info-bg text-info border-info-line',
  neutral: 'bg-surface-3 text-muted border-line',
}

const SIZE_STYLES = {
  sm: 'px-1.5 py-0.5 text-[10px]',
  md: 'px-2 py-0.5 text-[11px]',
}

export function Chip({
  tone = 'neutral',
  size = 'md',
  icon,
  className = '',
  children,
}: {
  tone?: Tone
  size?: 'sm' | 'md'
  icon?: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-sm border font-mono font-semibold uppercase tracking-wide ${TONE_STYLES[tone]} ${SIZE_STYLES[size]} ${className}`}
    >
      {icon}
      {children}
    </span>
  )
}

/**
 * A bare colored dot for dense rows where a full chip would be too heavy -
 * used in the Inbox queue and repository health lists.
 */
const DOT_STYLES: Record<Tone, string> = {
  pass: 'bg-pass',
  block: 'bg-block',
  warn: 'bg-warn',
  ai: 'bg-ai',
  accent: 'bg-accent',
  info: 'bg-info',
  neutral: 'bg-faint',
}

export function Dot({ tone = 'neutral', className = '' }: { tone?: Tone; className?: string }) {
  return <span className={`inline-block h-2 w-2 shrink-0 rounded-full ${DOT_STYLES[tone]} ${className}`} aria-hidden="true" />
}
