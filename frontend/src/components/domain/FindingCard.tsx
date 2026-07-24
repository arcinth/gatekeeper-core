import { useState } from 'react'
import { ChevronDown, ExternalLink } from 'lucide-react'
import { Chip } from '../ui/Chip'
import { humanize, type Tone } from '../ui/tones'

export interface FindingCardProps {
  /** Stable identifier of the rule that produced this - shown in mono. */
  ruleId: string
  severityLabel: string
  severityTone: Tone
  category?: string
  filePath: string
  lineNumber: number | null
  message: string
  recommendation: string | null
  /** Deep link to the exact line on GitHub, when the PR context is known. */
  sourceUrl?: string | null
  /** Blocking findings start expanded - the user should not have to hunt. */
  defaultExpanded?: boolean
}

/**
 * One consistent card shape for policy, security and AI findings alike
 * (Product Experience spec, §10). Identical skeleton across all three engines
 * is what makes a critical security finding and a trivial style nit feel like
 * the same product; only the tone and contents differ.
 */
export function FindingCard({
  ruleId,
  severityLabel,
  severityTone,
  category,
  filePath,
  lineNumber,
  message,
  recommendation,
  sourceUrl,
  defaultExpanded = false,
}: FindingCardProps) {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded)
  const hasDetail = Boolean(recommendation)

  return (
    <article className="rounded-lg border border-line bg-surface">
      <div className="flex items-start gap-3 p-4">
        <Chip tone={severityTone} className="mt-0.5">
          {severityLabel}
        </Chip>

        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium text-content">{message}</p>

          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-faint">
            <span className="truncate text-muted">
              {filePath}
              {lineNumber !== null && `:${lineNumber}`}
            </span>
            <span aria-hidden="true">·</span>
            <span>{ruleId}</span>
            {category && (
              <>
                <span aria-hidden="true">·</span>
                <span>{humanize(category)}</span>
              </>
            )}
          </div>

          {isExpanded && recommendation && (
            <div className="mt-3 rounded-md border border-line-soft bg-surface-2 p-3">
              <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Recommended fix</p>
              <p className="mt-1.5 text-sm text-muted">{recommendation}</p>
            </div>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-1">
          {sourceUrl && (
            <a
              href={sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              title="View on GitHub"
              aria-label="View on GitHub"
              className="rounded-md p-1.5 text-faint transition-colors hover:bg-surface-2 hover:text-content"
            >
              <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          )}
          {hasDetail && (
            <button
              type="button"
              onClick={() => setIsExpanded((current) => !current)}
              aria-expanded={isExpanded}
              aria-label={isExpanded ? 'Hide recommended fix' : 'Show recommended fix'}
              className="rounded-md p-1.5 text-faint transition-colors hover:bg-surface-2 hover:text-content"
            >
              <ChevronDown
                className={`h-4 w-4 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                aria-hidden="true"
              />
            </button>
          )}
        </div>
      </div>
    </article>
  )
}
