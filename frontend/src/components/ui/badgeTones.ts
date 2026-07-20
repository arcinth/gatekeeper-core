import type { AIReviewRunStatus } from '../../types/aiReviewRun'
import type { AIReviewConfidence } from '../../types/aiReviewFinding'
import type { AnalysisRunStatus, PullRequestStatus } from '../../types/analysisRun'
import type { AuditEventType } from '../../types/auditLog'
import type { GitHubInstallationStatus } from '../../types/githubInstallation'
import type { PolicySeverity } from '../../types/policyFinding'
import type { AiReviewStatus } from '../../types/report'
import type { ReviewDecisionType } from '../../types/reviewDecision'
import type { SecuritySeverity } from '../../types/securityFinding'
import type { VerdictOutcome } from '../../types/verdict'

/**
 * Every semantic color mapping in the app, consolidated from what were
 * previously identical `Record<Enum, string>` literals copy-pasted across
 * 3-6 pages each (e.g. the AnalysisRunStatus map appeared verbatim in
 * AnalysisRunsPage, PullRequestsPage, PullRequestDetailPage, and
 * RepositoryGovernancePage). The color choices themselves are unchanged -
 * this only removes the duplication, so every one of the deliberate
 * semantic-distinction decisions already made (severity gradient, AI's own
 * violet distinct from deterministic findings, verdict's emerald/red
 * governance pairing distinct from severity) carries over exactly.
 */

export const ANALYSIS_RUN_STATUS_TONES: Record<AnalysisRunStatus, string> = {
  RECEIVED: 'bg-slate-100 text-slate-700',
  QUEUED: 'bg-slate-100 text-slate-700',
  IN_PROGRESS: 'bg-amber-100 text-amber-800',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
}

export const VERDICT_OUTCOME_TONES: Record<VerdictOutcome, string> = {
  APPROVED: 'bg-emerald-100 text-emerald-800',
  BLOCKED: 'bg-red-100 text-red-800',
}

export const VERDICT_OUTCOME_BANNER_TONES: Record<VerdictOutcome, string> = {
  APPROVED: 'border-emerald-300 bg-emerald-50 text-emerald-900',
  BLOCKED: 'border-red-300 bg-red-50 text-red-900',
}

export const PULL_REQUEST_STATUS_TONES: Record<PullRequestStatus, string> = {
  OPEN: 'bg-emerald-100 text-emerald-800',
  CLOSED: 'bg-slate-100 text-slate-700',
  MERGED: 'bg-violet-100 text-violet-700',
}

// Shared by PolicySeverity and SecuritySeverity - both are the literal same
// four values with the same meaning (a deterministic finding's severity).
export const SEVERITY_TONES: Record<PolicySeverity | SecuritySeverity, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

// Deliberately violet throughout, distinct from every severity color used on
// the deterministic (Policy/Security) findings tables - AI Review results
// are advisory only and must read as visually distinct, not just
// differently labeled.
export const AI_CONFIDENCE_TONES: Record<AIReviewConfidence, string> = {
  LOW: 'bg-violet-50 text-violet-600',
  MEDIUM: 'bg-violet-100 text-violet-700',
  HIGH: 'bg-violet-200 text-violet-900',
}

export const AI_REVIEW_RUN_STATUS_TONES: Record<AIReviewRunStatus, string> = {
  COMPLETED: 'bg-violet-100 text-violet-800',
  FAILED: 'bg-red-100 text-red-800',
}

// Indigo distinguishes the Engineering Report's "meta" framing from
// severity's amber/orange/red, governance's emerald/red, and AI's own violet.
export const AI_REVIEW_REPORT_STATUS_TONES: Record<AiReviewStatus, string> = {
  INCLUDED: 'bg-indigo-100 text-indigo-800',
  UNAVAILABLE: 'bg-amber-100 text-amber-800',
  DISABLED: 'bg-slate-100 text-slate-600',
}

// Same green/red pairing as VERDICT_OUTCOME_TONES - a reviewer's APPROVED/
// REJECTED call reads with the same visual language as the engine's own verdict.
export const REVIEW_DECISION_TONES: Record<ReviewDecisionType, string> = {
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-800',
}

export const ACTIVE_STATE_TONES: Record<'active' | 'inactive', string> = {
  active: 'bg-emerald-100 text-emerald-800',
  inactive: 'bg-slate-100 text-slate-600',
}

// GitHub Connections status (Milestone 8: Repository Onboarding). ERROR reuses
// the same red as REPOSITORY_REMOVED/USER_REMOVED in AUDIT_EVENT_TYPE_TONES -
// both mean "needs attention now" - while SYNCING gets its own amber-in-motion
// framing distinct from the "something changed" amber used for *_UPDATED
// events, since a sync in progress isn't a problem, just not finished yet.
export const GITHUB_INSTALLATION_STATUS_TONES: Record<GitHubInstallationStatus, string> = {
  CONNECTING: 'bg-slate-100 text-slate-700',
  SYNCING: 'bg-amber-100 text-amber-800',
  ACTIVE: 'bg-emerald-100 text-emerald-800',
  ERROR: 'bg-red-100 text-red-800',
  DISCONNECTED: 'bg-slate-100 text-slate-600',
}

// Grouped by the dimension the event concerns, not by CRUD verb: governance
// outcomes (verdict/review decision) reuse the same emerald/red pairing as
// VERDICT_OUTCOME_TONES/REVIEW_DECISION_TONES; management actions (policy/
// repository/user/role) share a neutral indigo/slate/red family distinct
// from that governance pairing, since enabling a policy isn't a governance
// verdict.
export const AUDIT_EVENT_TYPE_TONES: Record<AuditEventType, string> = {
  ENGINEERING_REPORT_PUBLISHED: 'bg-indigo-100 text-indigo-800',
  VERDICT_PRODUCED: 'bg-indigo-100 text-indigo-800',
  REVIEW_DECISION_RECORDED: 'bg-indigo-100 text-indigo-800',
  POLICY_CONFIGURATION_CHANGED: 'bg-amber-100 text-amber-800',
  REPOSITORY_CONNECTED: 'bg-emerald-100 text-emerald-800',
  REPOSITORY_UPDATED: 'bg-amber-100 text-amber-800',
  REPOSITORY_REMOVED: 'bg-red-100 text-red-800',
  USER_CREATED: 'bg-emerald-100 text-emerald-800',
  USER_UPDATED: 'bg-amber-100 text-amber-800',
  USER_REMOVED: 'bg-red-100 text-red-800',
  ROLE_CREATED: 'bg-emerald-100 text-emerald-800',
  ROLE_UPDATED: 'bg-amber-100 text-amber-800',
  ROLE_REMOVED: 'bg-red-100 text-red-800',
}
