import type { AIReviewConfidence } from '../../types/aiReviewFinding'
import type { AIReviewRunStatus } from '../../types/aiReviewRun'
import type { AnalysisRunStatus, PullRequestStatus } from '../../types/analysisRun'
import type { AuditEventType } from '../../types/auditLog'
import type { GitHubInstallationStatus } from '../../types/githubInstallation'
import type { PolicySeverity } from '../../types/policyFinding'
import type { AiReviewStatus } from '../../types/report'
import type { ReviewDecisionType } from '../../types/reviewDecision'
import type { SecuritySeverity } from '../../types/securityFinding'
import type { VerdictOutcome } from '../../types/verdict'

/**
 * Semantic tones, not colors. Pages and components name the *meaning*
 * ('block', 'pass') and the design system owns which color that is in each
 * theme - the previous badgeTones.ts handed out raw Tailwind class strings
 * ('bg-red-100 text-red-800'), which made every consumer a place a color
 * decision could drift.
 *
 * The semantic distinctions established earlier in the product are preserved
 * exactly: a severity gradient, governance's pass/block pairing, and AI's own
 * tone kept deliberately separate from every deterministic signal so advisory
 * output can never be mistaken for a governance decision.
 */
export type Tone = 'pass' | 'block' | 'warn' | 'ai' | 'accent' | 'info' | 'neutral'

export function verdictOutcomeTone(outcome: VerdictOutcome): Tone {
  return outcome === 'APPROVED' ? 'pass' : 'block'
}

export function reviewDecisionTone(decision: ReviewDecisionType): Tone {
  return decision === 'APPROVED' ? 'pass' : 'block'
}

export function analysisRunStatusTone(status: AnalysisRunStatus): Tone {
  switch (status) {
    case 'COMPLETED':
      return 'pass'
    case 'FAILED':
      return 'block'
    case 'IN_PROGRESS':
      return 'warn'
    default:
      return 'neutral'
  }
}

export function pullRequestStatusTone(status: PullRequestStatus): Tone {
  switch (status) {
    case 'OPEN':
      return 'pass'
    case 'MERGED':
      return 'ai'
    default:
      return 'neutral'
  }
}

/** Shared by policy and security severity - the same four values with the same meaning. */
export function severityTone(severity: PolicySeverity | SecuritySeverity): Tone {
  switch (severity) {
    case 'CRITICAL':
      return 'block'
    case 'HIGH':
      return 'warn'
    case 'MEDIUM':
      return 'accent'
    default:
      return 'neutral'
  }
}

/** Always 'ai' - advisory output never borrows a severity color. */
export function aiConfidenceTone(_confidence: AIReviewConfidence): Tone {
  return 'ai'
}

export function aiReviewRunStatusTone(status: AIReviewRunStatus): Tone {
  return status === 'FAILED' ? 'block' : 'ai'
}

export function aiReviewStatusTone(status: AiReviewStatus): Tone {
  switch (status) {
    case 'INCLUDED':
      return 'ai'
    case 'UNAVAILABLE':
      return 'warn'
    default:
      return 'neutral'
  }
}

export function installationStatusTone(status: GitHubInstallationStatus): Tone {
  switch (status) {
    case 'ACTIVE':
      return 'pass'
    case 'ERROR':
      return 'block'
    case 'SYNCING':
      return 'warn'
    default:
      return 'neutral'
  }
}

export function auditEventTone(eventType: AuditEventType): Tone {
  switch (eventType) {
    case 'VERDICT_PRODUCED':
    case 'REVIEW_DECISION_RECORDED':
    case 'ENGINEERING_REPORT_PUBLISHED':
      return 'accent'
    case 'REPOSITORY_CONNECTED':
    case 'USER_CREATED':
    case 'ROLE_CREATED':
      return 'pass'
    case 'REPOSITORY_REMOVED':
    case 'USER_REMOVED':
    case 'ROLE_REMOVED':
      return 'block'
    default:
      return 'warn'
  }
}

/** Human-readable labels for enum values that would otherwise render as SCREAMING_SNAKE. */
export function humanize(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}
