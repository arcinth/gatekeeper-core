import type { ReactNode } from 'react'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import type { Tone } from '../ui/tones'

const VALUE_TONE: Record<Tone, string> = {
  pass: 'text-pass',
  block: 'text-block',
  warn: 'text-warn',
  ai: 'text-ai',
  accent: 'text-accent-hi',
  info: 'text-info',
  neutral: 'text-content',
}

/**
 * A single metric. Per the Product Experience spec's §06 rule, a stat earns
 * its place by being actionable or directional - so `caption` is where the
 * comparison or meaning goes, and a bare number with no context is a smell.
 */
export function StatTile({
  label,
  value,
  suffix,
  caption,
  tone = 'neutral',
  trend,
}: {
  label: string
  value: ReactNode
  suffix?: string
  caption?: ReactNode
  tone?: Tone
  /** Direction of movement, where a comparison is genuinely available. */
  trend?: { direction: 'up' | 'down' | 'flat'; label: string; isGood?: boolean }
}) {
  return (
    <div className="rounded-lg border border-line bg-surface p-4">
      <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">{label}</p>
      <p className={`tabular mt-2 text-2xl font-semibold tracking-tight ${VALUE_TONE[tone]}`}>
        {value}
        {suffix && <span className="ml-0.5 text-base font-medium text-muted">{suffix}</span>}
      </p>
      {trend && <TrendLine {...trend} />}
      {caption && <p className="mt-1.5 text-xs text-muted">{caption}</p>}
    </div>
  )
}

function TrendLine({ direction, label, isGood }: { direction: 'up' | 'down' | 'flat'; label: string; isGood?: boolean }) {
  const Icon = direction === 'up' ? ArrowUpRight : direction === 'down' ? ArrowDownRight : Minus
  const color = isGood === undefined ? 'text-muted' : isGood ? 'text-pass' : 'text-block'
  return (
    <p className={`mt-1.5 flex items-center gap-1 text-xs ${color}`}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {label}
    </p>
  )
}

/**
 * Horizontal proportion bar used for severity mixes and repository health -
 * the spec's preference for thin, restrained data-viz over heavy charting.
 */
export function ProportionBar({
  segments,
  className = '',
}: {
  segments: { tone: Tone; value: number; label: string }[]
  className?: string
}) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)
  if (total <= 0) {
    return <div className={`h-1.5 rounded-full bg-surface-3 ${className}`} />
  }

  const FILL: Record<Tone, string> = {
    pass: 'bg-pass',
    block: 'bg-block',
    warn: 'bg-warn',
    ai: 'bg-ai',
    accent: 'bg-accent',
    info: 'bg-info',
    neutral: 'bg-faint',
  }

  return (
    <div className={`flex h-1.5 overflow-hidden rounded-full bg-surface-3 ${className}`}>
      {segments
        .filter((segment) => segment.value > 0)
        .map((segment) => (
          <div
            key={segment.label}
            className={FILL[segment.tone]}
            style={{ width: `${(segment.value / total) * 100}%` }}
            title={`${segment.label}: ${segment.value}`}
          />
        ))}
    </div>
  )
}
